package lt.tsx.tennarc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe

@OptIn(ExperimentalComposeRuntimeApi::class)
class Recompositions {
    private val counts = HashMap<String, Int>()
    private val handles = mutableListOf<CompositionObserverHandle>()
    private var attached = false

    var measuring = false

    private val open = HashMap<RecomposeScope, String?>()

    var rechecks = 0
        private set

    var skips = 0
        private set

    val frames = mutableListOf<Int>()
    private var atFrameStart = 0

    private val observer = object : CompositionObserver {
        override fun onBeginComposition(composition: ObservableComposition) = Unit

        override fun onScopeEnter(scope: RecomposeScope) {
            if (!measuring) return
            val stack = Throwable().stackTrace
            if (stack.take(6).none { it.methodName == "enterRecomposeScope" }) {
                rechecks++
                return
            }
            open[scope] = entered(stack)
        }

        override fun onReadInScope(scope: RecomposeScope, value: Any) {
            if (measuring && open.containsKey(scope) && open[scope] == null) open[scope] = "${reader()} (lambda)"
        }

        override fun onScopeExit(scope: RecomposeScope) {
            if (!measuring || !open.containsKey(scope)) return
            val name = open.remove(scope) ?: "? (lambda reading nothing)"
            if (skipped(scope)) skips++ else counts.merge(name, 1, Int::plus)
        }

        override fun onEndComposition(composition: ObservableComposition) = Unit
        override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
        override fun onScopeDisposed(scope: RecomposeScope) = Unit
    }

    @OptIn(InternalComposeApi::class)
    @Composable
    fun Attach() {
        val composition = currentComposer.composition
        SideEffect { attach(composition) }
    }

    private fun attach(composition: Any) {
        if (attached) return
        attached = true
        val recomposer = composition.javaClass.getMethod("getParent").invoke(composition) as Recomposer
        handles += recomposer.observe(
            object : CompositionRegistrationObserver {
                override fun onCompositionRegistered(composition: ObservableComposition) {
                    handles += composition.setObserver(observer)
                }

                override fun onCompositionUnregistered(composition: ObservableComposition) = Unit
            },
        )
    }

    fun reset() {
        counts.clear()
        open.clear()
        frames.clear()
        atFrameStart = 0
        rechecks = 0
        skips = 0
    }

    fun frameDone() {
        if (!measuring) return
        frames += total - atFrameStart
        atFrameStart = total
    }

    val total: Int get() = counts.values.sum()

    fun of(name: String): Int = counts.entries
        .filter { !it.key.endsWith("(lambda)") && it.key.substringBefore(':').substringAfterLast('.').substringBefore('-') == name }
        .sumOf { it.value }

    val ours: Int get() = counts.filterKeys { it.startsWith(APP) }.values.sum()

    fun report(title: String): String = buildString {
        append("$title: $total composed, by frame $frames; $skips skipped, $rechecks rechecked")
        counts.entries.sortedByDescending { it.value }.take(40).forEach { (name, n) -> append("\n  $n  $name") }
    }

    private fun skipped(scope: RecomposeScope): Boolean {
        val read = skippedFlag ?: scope.javaClass.getMethod("getSkipped\$runtime").also { skippedFlag = it }
        return read.invoke(scope) as Boolean
    }

    private var skippedFlag: java.lang.reflect.Method? = null

    private fun entered(stack: Array<StackTraceElement>): String? {
        val frame = stack.firstOrNull { !it.className.isPlumbing() } ?: return null
        if (frame.className.startsWith("androidx.compose.ui.test") || frame.className.startsWith("kotlinx.coroutines")) return null
        return frame.named()
    }

    private fun reader(): String {
        val stack = Throwable().stackTrace.filterNot { it.className.isPlumbing() || it.className.endsWith("Test") || it.className.contains("Test$") }
        val first = stack.firstOrNull() ?: return "?"
        val app = stack.firstOrNull { it.className.startsWith(APP) }
        return if (app == null || app === first) first.named() else "${first.named()} in ${app.named()}"
    }

    private fun String.isPlumbing() = startsWith("androidx.compose.runtime") || startsWith(Recompositions::class.java.name) ||
        startsWith("java.") || startsWith("kotlin.") || startsWith("jdk.")

    private fun StackTraceElement.named(): String = "${className.substringBefore('$').removeSuffix("Kt")}.$methodName:$lineNumber"

    private companion object {
        const val APP = "lt.tsx.tennarc"
    }
}
