package lt.tsx.tennarc.tv.firetv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

class DadbShellTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var keyDir: File
    private lateinit var keys: AdbKeys

    @Before
    fun setUp() {
        assumeTrue(File("/bin/sh").canExecute())
        keyDir = folder.newFolder("adb")
        keys = AdbKeys(keyDir).also { it.pair() }
    }

    @Test
    fun `the probe tells a trusted key from an unknown one without offering it`() = runBlocking {
        FakeAdbd().use { tv ->
            assertFalse(DadbShell(LOOPBACK, keys, tv.port).trusted())
            tv.trust(publicKey())
            assertTrue(DadbShell(LOOPBACK, keys, tv.port).trusted())
            assertEquals(0, tv.prompts.get())
        }
    }

    @Test
    fun `a closed port means ADB debugging is off`() = runBlocking {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        try {
            DadbShell(LOOPBACK, keys, port).trusted()
            fail("connected to a closed port")
        } catch (_: AdbPortClosedException) {
        }
    }

    @Test
    fun `a port that wants TLS is told apart from one that's closed`() = runBlocking {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            thread(isDaemon = true) {
                runCatching {
                    server.accept().use { socket ->
                        DataInputStream(socket.getInputStream()).readFully(ByteArray(24 + "host::\u0000".length))
                        val stls = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(STLS).putInt(0x01000000).putInt(0).putInt(0).putInt(0).putInt(STLS.inv())
                        socket.getOutputStream().apply { write(stls.array()); flush() }
                        socket.getInputStream().read()
                    }
                }
            }
            try {
                DadbShell(LOOPBACK, keys, server.localPort).trusted()
                fail("took a TLS port for plain ADB")
            } catch (_: AdbTlsRequiredException) {
            }
        }
    }

    @Test
    fun `commands run in one long-lived shell`() = runBlocking {
        FakeAdbd().use { tv ->
            tv.trust(publicKey())
            val shell = DadbShell(LOOPBACK, keys, tv.port)
            shell.connect()
            assertEquals("hello", shell.shell("echo hello"))
            assertEquals("a;b 'c' \$HOME", shell.shell("printf '%s' ${shellQuote("a;b 'c' \$HOME")}"))
            assertEquals("to stderr", shell.shell("echo to stderr >&2"))
            assertEquals("", shell.shell("true"))
            assertEquals("line 1\nline 2", shell.shell("echo line 1; echo line 2"))
            assertEquals(1, tv.shellsOpened.get())
            shell.close()
        }
    }

    @Test
    fun `commands at the same time get a shell each, then share`() = runBlocking {
        FakeAdbd().use { tv ->
            tv.trust(publicKey())
            val shell = DadbShell(LOOPBACK, keys, tv.port)
            shell.connect()
            val outputs = listOf("sleep 0.3; echo slow", "echo fast").map { async(Dispatchers.IO) { shell.shell(it) } }.awaitAll()
            assertEquals(listOf("slow", "fast"), outputs)
            assertEquals("again", shell.shell("echo again"))
            assertEquals(2, tv.shellsOpened.get())
            shell.close()
        }
    }

    @Test
    fun `an unknown phone waits for the prompt`() = runBlocking {
        FakeAdbd().use { tv ->
            val shell = DadbShell(LOOPBACK, keys, tv.port)
            val connecting = async(Dispatchers.IO) { shell.connect() }
            assertTrue(tv.prompted.await(5, TimeUnit.SECONDS))
            assertFalse(connecting.isCompleted)
            tv.allow()
            withTimeout(5_000) { connecting.await() }
            assertEquals("allowed", shell.shell("echo allowed"))
            shell.close()
        }
    }

    @Test
    fun `giving up on the prompt returns at once, and the connection closes when the TV answers`() = runBlocking {
        FakeAdbd().use { tv ->
            val shell = DadbShell(LOOPBACK, keys, tv.port)
            val connecting = launch(Dispatchers.IO) { shell.connect() }
            assertTrue(tv.prompted.await(5, TimeUnit.SECONDS))
            withTimeout(1_000) { connecting.cancelAndJoin() }
            tv.allow()
            assertNotNull("the abandoned connection stayed open", tv.hungUp.poll(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `commands after close fail instead of reconnecting`() = runBlocking {
        FakeAdbd().use { tv ->
            tv.trust(publicKey())
            val shell = DadbShell(LOOPBACK, keys, tv.port)
            shell.connect()
            shell.close()
            try {
                shell.shell("echo nope")
                fail("ran after close")
            } catch (_: IOException) {
            }
            assertEquals(1, tv.connections.get())
        }
    }

    @Test
    fun `the key pair outlives the app, and forgetting makes a new one`() {
        val original = publicKey()
        val token = Random.nextBytes(20)
        val again = AdbKeys(keyDir)
        assertTrue(verifies(original, token, again.sign(token)))
        again.forget()
        val fresh = AdbKeys(keyDir)
        assertFalse(verifies(original, token, fresh.sign(token)))
        assertTrue(verifies(publicKey(), token, fresh.sign(token)))
    }

    private fun publicKey(): RSAPublicKey {
        val pem = File(keyDir, "adbkey").readText().replace(Regex("-----(BEGIN|END) PRIVATE KEY-----"), "").filterNot { it.isWhitespace() }
        val factory = KeyFactory.getInstance("RSA")
        val private = factory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))) as RSAPrivateCrtKey
        return factory.generatePublic(RSAPublicKeySpec(private.modulus, private.publicExponent)) as RSAPublicKey
    }

    private class FakeAdbd : Closeable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val trusted = ConcurrentHashMap.newKeySet<RSAPublicKey>()
        private val allowed = CountDownLatch(1)
        val port: Int get() = server.localPort
        val prompted = CountDownLatch(1)
        val prompts = AtomicInteger()
        val shellsOpened = AtomicInteger()
        val connections = AtomicInteger()
        val hungUp = LinkedBlockingQueue<Unit>()

        init {
            thread(isDaemon = true) {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    connections.incrementAndGet()
                    thread(isDaemon = true) { serve(socket) }
                }
            }
        }

        fun trust(key: RSAPublicKey) {
            trusted += key
        }

        fun allow() = allowed.countDown()

        override fun close() = server.close()

        private fun serve(socket: Socket) {
            val shells = ConcurrentHashMap<Int, Process>()
            try {
                socket.use {
                    val input = DataInputStream(socket.getInputStream())
                    val output = socket.getOutputStream()
                    check(read(input).command == CNXN)
                    val token = Random.nextBytes(20)
                    send(output, AUTH, AUTH_TOKEN, 0, token)
                    val signature = read(input)
                    if (trusted.none { verifies(it, token, signature.payload) }) {
                        send(output, AUTH, AUTH_TOKEN, 0, Random.nextBytes(20))
                        val offer = read(input)
                        check(offer.command == AUTH && offer.arg0 == AUTH_PUBLIC_KEY)
                        prompts.incrementAndGet()
                        prompted.countDown()
                        allowed.await()
                    }
                    send(output, CNXN, 0x01000000, 256 * 1024, "device::features=shell_v2,cmd;ro.product.name=fake".toByteArray())
                    var nextId = 100
                    while (true) {
                        val message = read(input)
                        when (message.command) {
                            OPEN -> {
                                val destination = String(message.payload).trimEnd('\u0000')
                                if (destination == "shell,v2,raw:") {
                                    shellsOpened.incrementAndGet()
                                    val id = nextId++
                                    val process = ProcessBuilder("/bin/sh").start()
                                    shells[message.arg0] = process
                                    send(output, OKAY, id, message.arg0, ByteArray(0))
                                    pump(output, process.inputStream, 1, id, message.arg0)
                                    pump(output, process.errorStream, 2, id, message.arg0)
                                } else {
                                    send(output, CLSE, 0, message.arg0, ByteArray(0))
                                }
                            }
                            WRTE -> {
                                send(output, OKAY, message.arg1, message.arg0, ByteArray(0))
                                val process = shells.getValue(message.arg0)
                                val packets = ByteBuffer.wrap(message.payload).order(ByteOrder.LITTLE_ENDIAN)
                                while (packets.hasRemaining()) {
                                    val id = packets.get().toInt()
                                    val data = ByteArray(packets.getInt()).also { packets.get(it) }
                                    if (id == 0) process.outputStream.apply { write(data); flush() }
                                }
                            }
                            CLSE -> shells.remove(message.arg0)?.destroy()
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                shells.values.forEach { it.destroy() }
                hungUp.add(Unit)
            }
        }

        private fun pump(output: OutputStream, stream: java.io.InputStream, packetId: Int, remoteId: Int, localId: Int) = thread(isDaemon = true) {
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    val packet = ByteBuffer.allocate(5 + count).order(ByteOrder.LITTLE_ENDIAN).put(packetId.toByte()).putInt(count).put(buffer, 0, count)
                    send(output, WRTE, remoteId, localId, packet.array())
                }
            } catch (_: IOException) {
            }
        }

        private class Message(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

        private fun read(input: DataInputStream): Message {
            val header = ByteBuffer.wrap(ByteArray(24).also(input::readFully)).order(ByteOrder.LITTLE_ENDIAN)
            val command = header.getInt()
            val arg0 = header.getInt()
            val arg1 = header.getInt()
            val length = header.getInt()
            header.getInt()
            check(header.getInt() == command.inv()) { "bad magic" }
            return Message(command, arg0, arg1, ByteArray(length).also(input::readFully))
        }

        private fun send(output: OutputStream, command: Int, arg0: Int, arg1: Int, payload: ByteArray) = synchronized(output) {
            val frame = ByteBuffer.allocate(24 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(command).putInt(arg0).putInt(arg1).putInt(payload.size)
                .putInt(payload.sumOf { it.toInt() and 0xFF }).putInt(command.inv()).put(payload)
            output.write(frame.array())
            output.flush()
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val WRTE = 0x45545257
        const val STLS = 0x534c5453
        const val AUTH_TOKEN = 1
        const val AUTH_PUBLIC_KEY = 3
        val SHA1_DIGEST_INFO = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)

        fun verifies(key: RSAPublicKey, token: ByteArray, signature: ByteArray): Boolean = runCatching {
            Signature.getInstance("NONEwithRSA").run {
                initVerify(key)
                update(SHA1_DIGEST_INFO + token)
                verify(signature)
            }
        }.getOrDefault(false)
    }
}
