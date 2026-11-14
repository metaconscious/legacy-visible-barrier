package io.github.metaconscious.lvb.ornithe;

/** World origin captured before a compiled vertex snapshot is published. */
public interface VertexOrigin {
    void lvb$setOrigin(double x, double y, double z);
    double lvb$originX();
    double lvb$originY();
    double lvb$originZ();
}
