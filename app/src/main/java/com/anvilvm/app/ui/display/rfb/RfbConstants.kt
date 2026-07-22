package com.anvilvm.app.ui.display.rfb

object RfbConstants {
    // RFB Protocol Version
    const val VERSION_3_8 = "RFB 003.008\n"

    // Security Types
    const val SECURITY_NONE = 1
    const val SECURITY_VNC_AUTH = 2

    // Client-to-Server message types
    const val MSG_SET_PIXEL_FORMAT = 0
    const val MSG_SET_ENCODINGS = 2
    const val MSG_FRAMEBUFFER_UPDATE_REQUEST = 3
    const val MSG_KEY_EVENT = 4
    const val MSG_POINTER_EVENT = 5
    const val MSG_CLIENT_CUT_TEXT = 6

    // Server-to-Client message types
    const val MSG_FRAMEBUFFER_UPDATE = 0
    const val MSG_SET_COLOUR_MAP = 1
    const val MSG_BELL = 2
    const val MSG_SERVER_CUT_TEXT = 3

    // Encoding types
    const val ENCODING_RAW = 0
    const val ENCODING_COPYRECT = 1
    const val ENCODING_RRE = 2
    const val ENCODING_HEXTILE = 5
    const val ENCODING_ZRLE = 16
    const val ENCODING_CURSOR = -239       // 0xFFFFFF11
    const val ENCODING_DESKTOP_SIZE = -223  // 0xFFFFFF21
}
