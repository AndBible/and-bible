package net.bible.sharedlogic.security

import okio.ByteString.Companion.decodeBase64

/** Fixed OpenSSL SHA1withRSA vectors; no runtime key generation or platform-specific signing in tests. */
object FeatureSignatureVectors {
    const val publicKeyPem = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsLbfb6YlNAhxXnTmulA3
aNzUzGCIIKwuCtjS/n7f3UfnLlRR2kLk28ZFD+hDm7g1YMjvDabJVMqEEKzT+4WK
mZE5a42vUC1fk3znr5iwz8olOQrAbayaix9Wnf1Rz4YAjxcquZXxZMIn5si/Fb4M
eay63SQMQTaLdZ+MC0RBnkRyEc5cz7GcOWa9xhJOi8LAmKmuN1efj3SsvtStG6BH
qNpeZVR2FmJ1UwwVlkGXRjHl2pxIZ3rpjBpUfC+acO+QPKM1jUXWHDJoQxNCGNZf
4UFmlE6Lpq7ALDwbyjWD04bLDSzZShSs8WIWzOv0cx7FjHmK6UOdZW1GUeivDIr8
/wIDAQAB
-----END PUBLIC KEY-----"""
    val signature: ByteArray get() = checkNotNull((
        "gAItOMVFjPMnG7Z7HqlEmvExDwx2sKFoetNXIrVFrdyoGVeZHcqPEUNZv8G9Hm4sUJT7KbcD9z1hIRWJ192qS6Mzl/3WGEOgZUCDROsP+lw2oSPs5/hRwXLEL0v+Lt1oIhOdQXSVg8cZ5CKCyeAk54zOLQNvMetbMUDAaRjb4OexOao1nZNMxZM83tXrHEKKNnOA5tu9FPivpsy3oGK5Zfwk/18N7232rHCNEblQPc/uZBHXI+ihpx6bfMEo1ncP7w7Ax4IEqs6HqqoO4IqhUHpnLbUipCoEfBbtDVtQgWuMF8BaDvxUE9Gv6J18cerp2237C3Fqls4gWAiwuFJ/1g=="
    ).decodeBase64()).toByteArray()
    val emptySignature: ByteArray get() = checkNotNull((
        "qvpKelX9DJFY8QzT7y5LuV/tObVrok3hmedDmXBp3qlLuCm8XLjRO7fDFhS1TS2CTQoiATtDDPnN5cZA/wTFFobJDaOfGgdiHc6mmFvLo2bMfqKIDa554/hcszVnG07quvMKOon8yVv2DiSH2FeCHfW0y22x3BGLCbjJsIPiy5xlG/KhyOfFkjEAJipSYPPT68obEYiz1gQgh0CvZ24sIeMVxDWdDnNtzlJdBu6TUm5k3/q6kchOKAkdQR8VLFVPM1SdYW8TXr43U98eIlW4slDKtIKvjC/exbNZTw1gxzGxDMd8bCiFSFdTz/bzLdapSXn5hKAx7OctXAMAiCFxxw=="
    ).decodeBase64()).toByteArray()
}
