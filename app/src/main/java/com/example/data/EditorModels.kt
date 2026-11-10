package com.example.data

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri

enum class GarmentCategory {
    DRESS, UPPER, LOWER, OUTERWEAR, UNDEFINED
}

enum class FabricStyle {
    NONE, DENIM, SILK, LEATHER, COTTON, SATIN
}

data class GarmentLayer(
    val id: Int,
    val assetId: String,
    val name: String,
    val category: GarmentCategory = GarmentCategory.UNDEFINED,
    val zIndex: Int = 0,
    var visible: Boolean = true,
    var opacity: Float = 1f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var rotationDeg: Float = 0f,
    var offset: PointF = PointF(0f, 0f),
    var hueShift: Float = 0f,
    var saturation: Float = 1f,
    var brightness: Float = 0f,
    var contrast: Float = 1f,
    var fabric: FabricStyle = FabricStyle.NONE
)

class LayerAssets {
    private val map = mutableMapOf<String, Pair<Bitmap, Bitmap?>>()

    fun put(assetId: String, garment: Bitmap, mask: Bitmap?) {
        map[assetId] = garment to mask
    }

    fun get(assetId: String): Pair<Bitmap, Bitmap?>? = map[assetId]

    fun clear() {
        map.values.forEach { (g, m) ->
            g.recycle()
            m?.recycle()
        }
        map.clear()
    }
}

data class EditSession(
    val base: Bitmap,
    val layers: List<GarmentLayer>,
    val assets: LayerAssets,
    val people: List<android.graphics.Rect>,
    val modelUri: Uri,
    val garmentUri: Uri,
    val personIndex: Int = 0
)

fun isValidSpaceId(spaceId: String): Boolean {
    return spaceId.matches(Regex("""^[\w-]+/[\w-]+$"""))
}
