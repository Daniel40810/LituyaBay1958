package com.dan.lituya.tools;

/**
 * Delaunay-Dreiecke über Punkten in der Ebene (Phase 2, für die Lotungen der NOS-Vermessungen), nach dem Verfahren der
 * wachsenden Hülle von V. Agafonkin („Delaunator“, ISC-Lizenz): Punkte nach Abstand vom Ausgangskreis sortiert, an die
 * Hülle angefügt und mit Kantenflips legalisiert. {@link #triangles} hält je Dreieck drei Punktnummern.
 * Gerechnet wird relativ zum Schwerpunkt, damit UTM-Koordinaten von 6,5 Mio. m nicht Stellen kosten.
 */
public final class Delaunay {
    private static final double EPS = Math.pow(2, -52);
    public final int[] triangles;
    private final double[] c;
    private final int[] halfedges, hullPrev, hullNext, hullTri, hullHash;
    private final int hashSize;
    private int trianglesLen, hullStart;
    private double cx, cy;
    private final int[] edgeStack = new int[512];

    public Delaunay(double[] xy) {
        int n = xy.length >> 1;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += xy[2 * i]; my += xy[2 * i + 1]; }
        mx /= Math.max(1, n);
        my /= Math.max(1, n);
        c = new double[xy.length];
        for (int i = 0; i < n; i++) { c[2 * i] = xy[2 * i] - mx; c[2 * i + 1] = xy[2 * i + 1] - my; }
        int maxT = Math.max(2 * n - 5, 0);
        int[] tri = new int[maxT * 3];
        halfedges = new int[maxT * 3];
        hashSize = (int) Math.ceil(Math.sqrt(n));
        hullPrev = new int[n];
        hullNext = new int[n];
        hullTri = new int[n];
        hullHash = new int[Math.max(1, hashSize)];
        java.util.Arrays.fill(hullHash, -1);
        tri = build(n, tri);
        triangles = java.util.Arrays.copyOf(tri, trianglesLen);
    }

    private int[] tris;

    private int[] build(int n, int[] tri) {
        tris = tri;
        if (n < 3) return tri;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            double x = c[2 * i], y = c[2 * i + 1];
            minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            ids[i] = i;
        }
        double ccx = (minX + maxX) / 2, ccy = (minY + maxY) / 2;
        int i0 = 0, i1 = 0, i2 = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double d = dist(ccx, ccy, c[2 * i], c[2 * i + 1]);
            if (d < minDist) { i0 = i; minDist = d; }
        }
        double i0x = c[2 * i0], i0y = c[2 * i0 + 1];
        minDist = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (i == i0) continue;
            double d = dist(i0x, i0y, c[2 * i], c[2 * i + 1]);
            if (d < minDist && d > 0) { i1 = i; minDist = d; }
        }
        double i1x = c[2 * i1], i1y = c[2 * i1 + 1];
        double minRadius = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (i == i0 || i == i1) continue;
            double r = circumradius(i0x, i0y, i1x, i1y, c[2 * i], c[2 * i + 1]);
            if (r < minRadius) { i2 = i; minRadius = r; }
        }
        if (minRadius == Double.MAX_VALUE) return tri;   // alle auf einer Linie
        double i2x = c[2 * i2], i2y = c[2 * i2 + 1];
        if (orient(i0x, i0y, i1x, i1y, i2x, i2y)) {
            int t = i1; double tx = i1x, ty = i1y;
            i1 = i2; i1x = i2x; i1y = i2y;
            i2 = t; i2x = tx; i2y = ty;
        }
        double[] cc = circumcenter(i0x, i0y, i1x, i1y, i2x, i2y);
        cx = cc[0];
        cy = cc[1];
        double[] dists = new double[n];
        for (int i = 0; i < n; i++) dists[i] = dist(c[2 * i], c[2 * i + 1], cx, cy);
        Integer[] boxed = new Integer[n];
        for (int i = 0; i < n; i++) boxed[i] = i;
        java.util.Arrays.sort(boxed, (a, b) -> Double.compare(dists[a], dists[b]));
        for (int i = 0; i < n; i++) ids[i] = boxed[i];
        hullStart = i0;
        hullNext[i0] = hullPrev[i2] = i1;
        hullNext[i1] = hullPrev[i0] = i2;
        hullNext[i2] = hullPrev[i1] = i0;
        hullTri[i0] = 0;
        hullTri[i1] = 1;
        hullTri[i2] = 2;
        hullHash[hashKey(i0x, i0y)] = i0;
        hullHash[hashKey(i1x, i1y)] = i1;
        hullHash[hashKey(i2x, i2y)] = i2;
        trianglesLen = 0;
        addTriangle(i0, i1, i2, -1, -1, -1);
        double xp = 0, yp = 0;
        for (int k = 0; k < n; k++) {
            int i = ids[k];
            double x = c[2 * i], y = c[2 * i + 1];
            if (k > 0 && Math.abs(x - xp) <= EPS && Math.abs(y - yp) <= EPS) continue;
            xp = x;
            yp = y;
            if (i == i0 || i == i1 || i == i2) continue;
            int start = 0;
            for (int j = 0, key = hashKey(x, y); j < hashSize; j++) {
                start = hullHash[(key + j) % hashSize];
                if (start != -1 && start != hullNext[start]) break;
            }
            start = hullPrev[start];
            int e = start, q;
            while (true) {
                q = hullNext[e];
                if (orient(x, y, c[2 * e], c[2 * e + 1], c[2 * q], c[2 * q + 1])) break;
                e = q;
                if (e == start) { e = -1; break; }
            }
            if (e == -1) continue;
            int t = addTriangle(e, i, hullNext[e], -1, -1, hullTri[e]);
            hullTri[i] = legalize(t + 2);
            hullTri[e] = t;
            int nn = hullNext[e];
            while (true) {
                q = hullNext[nn];
                if (!orient(x, y, c[2 * nn], c[2 * nn + 1], c[2 * q], c[2 * q + 1])) break;
                t = addTriangle(nn, i, q, hullTri[i], -1, hullTri[nn]);
                hullTri[i] = legalize(t + 2);
                hullNext[nn] = nn;
                nn = q;
            }
            if (e == start) {
                while (true) {
                    q = hullPrev[e];
                    if (!orient(x, y, c[2 * q], c[2 * q + 1], c[2 * e], c[2 * e + 1])) break;
                    t = addTriangle(q, i, e, -1, hullTri[e], hullTri[q]);
                    legalize(t + 2);
                    hullTri[q] = t;
                    hullNext[e] = e;
                    e = q;
                }
            }
            hullStart = hullPrev[i] = e;
            hullNext[e] = hullPrev[nn] = i;
            hullNext[i] = nn;
            hullHash[hashKey(x, y)] = i;
            hullHash[hashKey(c[2 * e], c[2 * e + 1])] = e;
        }
        return tris;
    }

    private int hashKey(double x, double y) {
        return (int) (Math.floor(pseudoAngle(x - cx, y - cy) * hashSize) % hashSize);
    }

    private static double pseudoAngle(double dx, double dy) {
        double p = dx / (Math.abs(dx) + Math.abs(dy));
        return (dy > 0 ? 3 - p : 1 + p) / 4;
    }

    private int legalize(int a) {
        int i = 0, ar;
        while (true) {
            int b = halfedges[a];
            int a0 = a - a % 3;
            ar = a0 + (a + 2) % 3;
            if (b == -1) {
                if (i == 0) break;
                a = edgeStack[--i];
                continue;
            }
            int b0 = b - b % 3, al = a0 + (a + 1) % 3, bl = b0 + (b + 2) % 3;
            int p0 = tris[ar], pr = tris[a], pl = tris[al], p1 = tris[bl];
            boolean illegal = inCircle(c[2 * p0], c[2 * p0 + 1], c[2 * pr], c[2 * pr + 1], c[2 * pl], c[2 * pl + 1], c[2 * p1], c[2 * p1 + 1]);
            if (illegal) {
                tris[a] = p1;
                tris[b] = p0;
                int hbl = halfedges[bl];
                if (hbl == -1) {
                    int e = hullStart;
                    do {
                        if (hullTri[e] == bl) { hullTri[e] = a; break; }
                        e = hullPrev[e];
                    } while (e != hullStart);
                }
                link(a, hbl);
                link(b, halfedges[ar]);
                link(ar, bl);
                int br = b0 + (b + 1) % 3;
                if (i < edgeStack.length) edgeStack[i++] = br;
            } else {
                if (i == 0) break;
                a = edgeStack[--i];
            }
        }
        return ar;
    }

    private void link(int a, int b) {
        halfedges[a] = b;
        if (b != -1) halfedges[b] = a;
    }

    private int addTriangle(int i0, int i1, int i2, int a, int b, int cc) {
        int t = trianglesLen;
        tris[t] = i0;
        tris[t + 1] = i1;
        tris[t + 2] = i2;
        link(t, a);
        link(t + 1, b);
        link(t + 2, cc);
        trianglesLen += 3;
        return t;
    }

    private static double dist(double ax, double ay, double bx, double by) {
        double dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static boolean orient(double px, double py, double qx, double qy, double rx, double ry) {
        return (qy - py) * (rx - qx) - (qx - px) * (ry - qy) < 0;
    }

    private static boolean inCircle(double ax, double ay, double bx, double by, double cx, double cy, double px, double py) {
        double dx = ax - px, dy = ay - py, ex = bx - px, ey = by - py, fx = cx - px, fy = cy - py;
        double ap = dx * dx + dy * dy, bp = ex * ex + ey * ey, cp = fx * fx + fy * fy;
        return dx * (ey * cp - bp * fy) - dy * (ex * cp - bp * fx) + ap * (ex * fy - ey * fx) < 0;
    }

    private static double circumradius(double ax, double ay, double bx, double by, double cx, double cy) {
        double dx = bx - ax, dy = by - ay, ex = cx - ax, ey = cy - ay, bl = dx * dx + dy * dy, cl = ex * ex + ey * ey;
        double d = 0.5 / (dx * ey - dy * ex), x = (ey * bl - dy * cl) * d, y = (dx * cl - ex * bl) * d;
        double r = x * x + y * y;
        return Double.isNaN(r) || Double.isInfinite(r) ? Double.MAX_VALUE : r;
    }

    private static double[] circumcenter(double ax, double ay, double bx, double by, double cx, double cy) {
        double dx = bx - ax, dy = by - ay, ex = cx - ax, ey = cy - ay, bl = dx * dx + dy * dy, cl = ex * ex + ey * ey;
        double d = 0.5 / (dx * ey - dy * ex);
        return new double[]{ax + (ey * bl - dy * cl) * d, ay + (dx * cl - ex * bl) * d};
    }

    /**
     * Linear über die Dreiecke auf ein Gitter: jede Zellmitte in einem Dreieck bekommt den Wert der Ebene durch seine
     * Ecken. Dreiecke mit einer Kante über maxKante (m) bleiben leer (dort fehlen Lotungen). xy und z wie im Konstruktor.
     */
    public static void aufGitter(double[] xy, double[] z, Gitter g, double maxKante, float[] ziel) {
        Delaunay d = new Delaunay(xy);
        int[] t = d.triangles;
        double m2 = maxKante * maxKante;
        for (int k = 0; k < t.length; k += 3) {
            int a = t[k], b = t[k + 1], cc = t[k + 2];
            double ax = xy[2 * a], ay = xy[2 * a + 1], bx = xy[2 * b], by = xy[2 * b + 1], cx = xy[2 * cc], cy = xy[2 * cc + 1];
            if (dist(ax, ay, bx, by) > m2 || dist(bx, by, cx, cy) > m2 || dist(ax, ay, cx, cy) > m2) continue;
            double det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
            if (Math.abs(det) < 1e-9) continue;
            int i0 = (int) Math.floor((Math.min(ax, Math.min(bx, cx)) - g.e0()) / g.zelle() - 0.5), i1 = (int) Math.ceil((Math.max(ax, Math.max(bx, cx)) - g.e0()) / g.zelle() - 0.5);
            int j0 = (int) Math.floor((g.n0() - Math.max(ay, Math.max(by, cy))) / g.zelle() - 0.5), j1 = (int) Math.ceil((g.n0() - Math.min(ay, Math.min(by, cy))) / g.zelle() - 0.5);
            i0 = Math.max(0, i0); j0 = Math.max(0, j0); i1 = Math.min(g.w() - 1, i1); j1 = Math.min(g.h() - 1, j1);
            for (int j = j0; j <= j1; j++)
                for (int i = i0; i <= i1; i++) {
                    double px = g.ce(i), py = g.cn(j);
                    double l1 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / det, l2 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / det, l3 = 1 - l1 - l2;
                    if (l1 < -1e-9 || l2 < -1e-9 || l3 < -1e-9) continue;
                    ziel[j * g.w() + i] = (float) (l1 * z[a] + l2 * z[b] + l3 * z[cc]);
                }
        }
    }
}
