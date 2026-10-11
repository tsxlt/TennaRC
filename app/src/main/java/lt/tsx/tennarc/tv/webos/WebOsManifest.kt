package lt.tsx.tennarc.tv.webos
import org.json.JSONArray
import org.json.JSONObject

internal object WebOsManifest {
    private const val SIGNATURE =
        "eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbm" +
            "ctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR" +
            "+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRy" +
            "aMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4" +
            "RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n" +
            "50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM" +
            "2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQoj" +
            "oa7NQnAtw=="

    private val PERMISSIONS = listOf(
        "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CLOSE", "TEST_OPEN", "TEST_PROTECTED",
        "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_RECORDING",
        "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER", "CONTROL_TV_SCREEN",
        "READ_APP_STATUS", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE",
        "READ_RUNNING_APPS", "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE",
        "READ_COUNTRY_INFO", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "READ_INSTALLED_APPS",
        "READ_SETTINGS", "READ_STORAGE_DEVICE_LIST",
    )

    private val SIGNED_PERMISSIONS = listOf(
        "TEST_SECURE", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "READ_INSTALLED_APPS",
        "READ_LGE_SDX", "READ_NOTIFICATIONS", "SEARCH", "WRITE_SETTINGS", "WRITE_NOTIFICATION_ALERT",
        "CONTROL_POWER", "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS", "READ_UPDATE_INFO",
        "UPDATE_FROM_REMOTE_APP", "READ_LGE_TV_INPUT_EVENTS", "READ_TV_CURRENT_TIME",
    )

    fun json(deviceName: String, signed: Boolean = true): JSONObject {
        val manifest = JSONObject()
            .put("appVersion", "1.1")
            .put("manifestVersion", 1)
            .put("deviceName", deviceName)
            .put("permissions", JSONArray(PERMISSIONS))
        return if (signed) withSignature(manifest) else manifest
    }

    private fun withSignature(manifest: JSONObject): JSONObject = manifest
        .put("signatures", JSONArray().put(JSONObject().put("signature", SIGNATURE).put("signatureVersion", 1)))
        .put(
            "signed",
            JSONObject()
                .put("appId", "com.lge.test")
                .put("created", "20140509")
                .put(
                    "localizedAppNames",
                    JSONObject().put("", "LG Remote App").put("ko-KR", "리모컨 앱").put("zxx-XX", "ЛГ Rэмotэ AПП"),
                )
                .put("localizedVendorNames", JSONObject().put("", "LG Electronics"))
                .put("permissions", JSONArray(SIGNED_PERMISSIONS))
                .put("serial", "2f930e2d2cfe083771f68e4fe7bb07")
                .put("vendorId", "com.lge"),
        )
}
