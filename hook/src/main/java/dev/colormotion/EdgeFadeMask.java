package dev.colormotion;

/** A continuous notification edge mask, transparent everywhere above/below its fade bands. */
final class EdgeFadeMask {
    final int[] colors;
    final float[] positions;

    EdgeFadeMask(int height, int topStart, int topEnd, int bottomStart) {
        float h = Math.max(1, height);
        float[] points = new float[13];
        int count = 0;
        points[count++] = 0f;
        for (int i = 0; i <= 4; i++) points[count++] = QsModel.clamp(
                (topStart + (topEnd - topStart) * i / 4f) / h, 0f, 1f);
        for (int i = 0; i <= 4; i++) points[count++] = QsModel.clamp(
                (bottomStart + (h - bottomStart) * i / 4f) / h, 0f, 1f);
        points[count++] = 1f;
        java.util.Arrays.sort(points, 0, count);
        int unique = 0;
        for (int i = 0; i < count; i++)
            if (unique == 0 || points[i] > points[unique - 1]) points[unique++] = points[i];
        positions = java.util.Arrays.copyOf(points, unique);
        colors = new int[unique];
        for (int i = 0; i < unique; i++) {
            float y = positions[i] * h;
            float top = QsModel.clamp((y - topStart) / Math.max(1f, topEnd - topStart), 0f, 1f);
            float bottom = QsModel.clamp((h - y) / Math.max(1f, h - bottomStart), 0f, 1f);
            int alpha = Math.round(255f * (float) (Math.pow(top, 2.5) * Math.pow(bottom, 2.5)));
            colors[i] = alpha << 24;
        }
    }
}
