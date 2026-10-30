package com.example.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

object QrCodeHelper {

    fun generateQrBitmap(
        content: String,
        width: Int = 512,
        height: Int = 512,
        fgColor: Int = Color.BLACK,
        bgColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val bitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                width,
                height
            )
            val w = bitMatrix.width
            val h = bitMatrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val offset = y * w
                for (x in 0 until w) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) fgColor else bgColor
                }
            }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decodeBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val hints = mapOf<com.google.zxing.DecodeHintType, Any>(
                com.google.zxing.DecodeHintType.TRY_HARDER to true,
                com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.AZTEC,
                    BarcodeFormat.PDF_417,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                    BarcodeFormat.ITF
                )
            )
            val reader = MultiFormatReader().apply { setHints(hints) }
            val result = try {
                reader.decodeWithState(binaryBitmap)
            } catch (_: Exception) {
                reader.decodeWithState(BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source)))
            }
            result.text
        } catch (e: Exception) {
            null
        }
    }

    fun formatWifiString(ssid: String, password: String, securityType: String): String {
        val type = when (securityType.uppercase()) {
            "WPA/WPA2" -> "WPA"
            "WEP" -> "WEP"
            else -> "nopass"
        }
        return "WIFI:T:$type;S:$ssid;P:$password;;"
    }

    fun formatVCard(name: String, phone: String, email: String, org: String): String {
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$name")
            if (phone.isNotBlank()) appendLine("TEL:$phone")
            if (email.isNotBlank()) appendLine("EMAIL:$email")
            if (org.isNotBlank()) appendLine("ORG:$org")
            appendLine("END:VCARD")
        }
    }
}
