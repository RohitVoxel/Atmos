package net.atmos.render.gpu;

/** Identifies when a cloud layer's baked geometry must be rebuilt (scale/opacity/texture/brightness/render-distance changed). */
public final class CloudMeshBuilder {

    private CloudMeshBuilder() {}

    public static int fingerprint(net.atmos.cloud.CloudLayer layer, float maxExtent, float brightness) {
        int h = layer.texture() != null ? layer.texture().hashCode() : 0;
        h = 31 * h + Float.floatToIntBits(layer.scale());
        h = 31 * h + Float.floatToIntBits(layer.opacity());
        h = 31 * h + Float.floatToIntBits(layer.height());
        h = 31 * h + Float.floatToIntBits(maxExtent);
        h = 31 * h + Float.floatToIntBits(brightness);
        return h;
    }
}