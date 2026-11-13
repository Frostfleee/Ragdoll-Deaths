package com.frostfleee.ragdolldeaths.physics;

/**
 * Contact points between two oriented boxes, by the separating-axis test. Box A is always a body; box B is another
 * body or a fixed world box. The result is a shared normal pointing from B to A and up to eight point pairs, one point
 * on each box's surface. Boxes that are apart by less than the margin still give contacts (with a positive distance),
 * so a fast body knows about the floor before it reaches it.
 */
final class BoxCollision {
	final double[] ca = new double[3], cb = new double[3];
	/** The three axes of each box in world space, one per row. */
	final double[] xa = new double[9], xb = new double[9];
	final double[] ha = new double[3], hb = new double[3];

	int count;
	double nx, ny, nz;
	final double[] pa = new double[24], pb = new double[24];
	/** Signed distance of each pair along the normal: negative when the boxes overlap there. */
	final double[] depth = new double[8];

	private final double[] ar = new double[9];
	private double[] polyIn = new double[48], polyOut = new double[48];

	void setA(Body b) {
		fill(b, ca, xa, ha);
	}

	void setB(Body b) {
		fill(b, cb, xb, hb);
	}

	void setB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		cb[0] = (minX + maxX) * 0.5;
		cb[1] = (minY + maxY) * 0.5;
		cb[2] = (minZ + maxZ) * 0.5;
		hb[0] = (maxX - minX) * 0.5;
		hb[1] = (maxY - minY) * 0.5;
		hb[2] = (maxZ - minZ) * 0.5;
		xb[0] = 1;
		xb[1] = 0;
		xb[2] = 0;
		xb[3] = 0;
		xb[4] = 1;
		xb[5] = 0;
		xb[6] = 0;
		xb[7] = 0;
		xb[8] = 1;
	}

	private static void fill(Body b, double[] c, double[] x, double[] h) {
		c[0] = b.x;
		c[1] = b.y;
		c[2] = b.z;
		h[0] = b.hx;
		h[1] = b.hy;
		h[2] = b.hz;
		double xx = b.qx * b.qx, yy = b.qy * b.qy, zz = b.qz * b.qz;
		double xy = b.qx * b.qy, xz = b.qx * b.qz, yz = b.qy * b.qz;
		double wx = b.qw * b.qx, wy = b.qw * b.qy, wz = b.qw * b.qz;
		x[0] = 1 - 2 * (yy + zz);
		x[1] = 2 * (xy + wz);
		x[2] = 2 * (xz - wy);
		x[3] = 2 * (xy - wz);
		x[4] = 1 - 2 * (xx + zz);
		x[5] = 2 * (yz + wx);
		x[6] = 2 * (xz + wy);
		x[7] = 2 * (yz - wx);
		x[8] = 1 - 2 * (xx + yy);
	}

	/**
	 * Contacts that push box A out through the top face of box B, whatever the shortest way out is. Used for a part
	 * whose middle is inside a block with open air above it: a thin limb buried in the ground has its shortest way
	 * out to the side, into the next block, and would never get out.
	 */
	boolean collideUpward(double margin) {
		count = 0;
		forcedSign = 1;
		faceContacts(false, 1, margin);
		forcedSign = 0;
		return count > 0;
	}

	/** When not zero, the side of the reference box the contact is made on, instead of the side facing the other box. */
	private int forcedSign;

	boolean collide(double margin) {
		count = 0;
		double tx = cb[0] - ca[0], ty = cb[1] - ca[1], tz = cb[2] - ca[2];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				ar[i * 3 + j] = Math.abs(xa[i * 3] * xb[j * 3] + xa[i * 3 + 1] * xb[j * 3 + 1] + xa[i * 3 + 2] * xb[j * 3 + 2])
						+ 1.0e-9;
			}
		}
		// The axis the boxes overlap least along (or are furthest apart along) is where they touch.
		double best = -Double.MAX_VALUE;
		int bestAxis = -1;
		for (int i = 0; i < 3; i++) {
			double d = tx * xa[i * 3] + ty * xa[i * 3 + 1] + tz * xa[i * 3 + 2];
			double sep = Math.abs(d) - (ha[i] + hb[0] * ar[i * 3] + hb[1] * ar[i * 3 + 1] + hb[2] * ar[i * 3 + 2]);
			if (sep > margin) {
				return false;
			}
			if (sep > best) {
				best = sep;
				bestAxis = i;
			}
		}
		for (int j = 0; j < 3; j++) {
			double d = tx * xb[j * 3] + ty * xb[j * 3 + 1] + tz * xb[j * 3 + 2];
			double sep = Math.abs(d) - (hb[j] + ha[0] * ar[j] + ha[1] * ar[3 + j] + ha[2] * ar[6 + j]);
			if (sep > margin) {
				return false;
			}
			if (sep > best) {
				best = sep;
				bestAxis = 3 + j;
			}
		}
		double bestEdge = -Double.MAX_VALUE;
		int ei = -1, ej = -1;
		double ex = 0, ey = 0, ez = 0;
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				double lx = xa[i * 3 + 1] * xb[j * 3 + 2] - xa[i * 3 + 2] * xb[j * 3 + 1];
				double ly = xa[i * 3 + 2] * xb[j * 3] - xa[i * 3] * xb[j * 3 + 2];
				double lz = xa[i * 3] * xb[j * 3 + 1] - xa[i * 3 + 1] * xb[j * 3];
				double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
				if (len < 1.0e-6) {
					continue;
				}
				lx /= len;
				ly /= len;
				lz /= len;
				double d = tx * lx + ty * ly + tz * lz;
				double ra = 0, rb = 0;
				for (int k = 0; k < 3; k++) {
					ra += ha[k] * Math.abs(xa[k * 3] * lx + xa[k * 3 + 1] * ly + xa[k * 3 + 2] * lz);
					rb += hb[k] * Math.abs(xb[k * 3] * lx + xb[k * 3 + 1] * ly + xb[k * 3 + 2] * lz);
				}
				double sep = Math.abs(d) - ra - rb;
				if (sep > margin) {
					return false;
				}
				if (sep > bestEdge) {
					bestEdge = sep;
					ei = i;
					ej = j;
					double s = d > 0 ? -1 : 1;
					ex = lx * s;
					ey = ly * s;
					ez = lz * s;
				}
			}
		}
		// Faces give a steadier, multi-point contact, so an edge pair must be clearly the better axis to win.
		if (ei >= 0 && bestEdge > best + 0.005 + 0.05 * Math.abs(best)) {
			edgeContact(ei, ej, ex, ey, ez, bestEdge);
		} else {
			faceContacts(bestAxis < 3, bestAxis % 3, margin);
		}
		return count > 0;
	}

	private void faceContacts(boolean refIsA, int axis, double margin) {
		double[] cr = refIsA ? ca : cb, xr = refIsA ? xa : xb, hr = refIsA ? ha : hb;
		double[] ci = refIsA ? cb : ca, xi = refIsA ? xb : xa, hi = refIsA ? hb : ha;
		double ax = xr[axis * 3], ay = xr[axis * 3 + 1], az = xr[axis * 3 + 2];
		double s = forcedSign != 0 ? forcedSign : (ci[0] - cr[0]) * ax + (ci[1] - cr[1]) * ay + (ci[2] - cr[2]) * az >= 0 ? 1 : -1;
		// The reference face's outward normal, towards the other box.
		double rx = ax * s, ry = ay * s, rz = az * s;
		nx = refIsA ? -rx : rx;
		ny = refIsA ? -ry : ry;
		nz = refIsA ? -rz : rz;

		// The other box's face that looks back at it most squarely.
		int k = 0;
		double bestDot = 0;
		for (int m = 0; m < 3; m++) {
			double d = xi[m * 3] * rx + xi[m * 3 + 1] * ry + xi[m * 3 + 2] * rz;
			if (Math.abs(d) > Math.abs(bestDot)) {
				bestDot = d;
				k = m;
			}
		}
		double sg = bestDot > 0 ? -1 : 1;
		int u = (k + 1) % 3, v = (k + 2) % 3;
		double fx = ci[0] + sg * hi[k] * xi[k * 3], fy = ci[1] + sg * hi[k] * xi[k * 3 + 1], fz = ci[2] + sg * hi[k] * xi[k * 3 + 2];
		double ux = hi[u] * xi[u * 3], uy = hi[u] * xi[u * 3 + 1], uz = hi[u] * xi[u * 3 + 2];
		double vx = hi[v] * xi[v * 3], vy = hi[v] * xi[v * 3 + 1], vz = hi[v] * xi[v * 3 + 2];
		double[] in = polyIn, out = polyOut;
		in[0] = fx + ux + vx;
		in[1] = fy + uy + vy;
		in[2] = fz + uz + vz;
		in[3] = fx - ux + vx;
		in[4] = fy - uy + vy;
		in[5] = fz - uz + vz;
		in[6] = fx - ux - vx;
		in[7] = fy - uy - vy;
		in[8] = fz - uz - vz;
		in[9] = fx + ux - vx;
		in[10] = fy + uy - vy;
		in[11] = fz + uz - vz;
		int n = 4;
		// Cut that face down to the part over the reference face.
		for (int step = 1; step <= 2 && n > 0; step++) {
			int m = (axis + step) % 3;
			double mx = xr[m * 3], my = xr[m * 3 + 1], mz = xr[m * 3 + 2];
			double c = cr[0] * mx + cr[1] * my + cr[2] * mz;
			n = clip(in, n, out, mx, my, mz, c + hr[m]);
			double[] swap = in;
			in = out;
			out = swap;
			n = clip(in, n, out, -mx, -my, -mz, -c + hr[m]);
			swap = in;
			in = out;
			out = swap;
		}
		double refOff = cr[0] * rx + cr[1] * ry + cr[2] * rz + hr[axis];
		for (int p = 0; p < n && count < 8; p++) {
			double qx = in[p * 3], qy = in[p * 3 + 1], qz = in[p * 3 + 2];
			double dist = qx * rx + qy * ry + qz * rz - refOff;
			if (dist > margin) {
				continue;
			}
			double[] onRef = refIsA ? pa : pb, onInc = refIsA ? pb : pa;
			int at = count * 3;
			onInc[at] = qx;
			onInc[at + 1] = qy;
			onInc[at + 2] = qz;
			onRef[at] = qx - rx * dist;
			onRef[at + 1] = qy - ry * dist;
			onRef[at + 2] = qz - rz * dist;
			depth[count++] = dist;
		}
	}

	/** Keeps the part of a polygon on the inner side of a plane (normal · p <= offset). */
	private static int clip(double[] in, int n, double[] out, double px, double py, double pz, double off) {
		int m = 0;
		for (int i = 0; i < n; i++) {
			int c = i * 3, p = ((i + n - 1) % n) * 3;
			double dc = in[c] * px + in[c + 1] * py + in[c + 2] * pz - off;
			double dp = in[p] * px + in[p + 1] * py + in[p + 2] * pz - off;
			if ((dc <= 0) != (dp <= 0)) {
				double t = dp / (dp - dc);
				out[m * 3] = in[p] + t * (in[c] - in[p]);
				out[m * 3 + 1] = in[p + 1] + t * (in[c + 1] - in[p + 1]);
				out[m * 3 + 2] = in[p + 2] + t * (in[c + 2] - in[p + 2]);
				m++;
			}
			if (dc <= 0) {
				out[m * 3] = in[c];
				out[m * 3 + 1] = in[c + 1];
				out[m * 3 + 2] = in[c + 2];
				m++;
			}
		}
		return m;
	}

	/** One contact where an edge of A crosses an edge of B: the closest points of the two edges. */
	private void edgeContact(int i, int j, double ex, double ey, double ez, double sep) {
		nx = ex;
		ny = ey;
		nz = ez;
		// A's edge nearest B, and B's edge nearest A.
		double ax = ca[0], ay = ca[1], az = ca[2];
		double bx = cb[0], by = cb[1], bz = cb[2];
		for (int k = 0; k < 3; k++) {
			if (k != i) {
				double s = xa[k * 3] * nx + xa[k * 3 + 1] * ny + xa[k * 3 + 2] * nz > 0 ? -ha[k] : ha[k];
				ax += s * xa[k * 3];
				ay += s * xa[k * 3 + 1];
				az += s * xa[k * 3 + 2];
			}
			if (k != j) {
				double s = xb[k * 3] * nx + xb[k * 3 + 1] * ny + xb[k * 3 + 2] * nz > 0 ? hb[k] : -hb[k];
				bx += s * xb[k * 3];
				by += s * xb[k * 3 + 1];
				bz += s * xb[k * 3 + 2];
			}
		}
		double d1x = xa[i * 3], d1y = xa[i * 3 + 1], d1z = xa[i * 3 + 2];
		double d2x = xb[j * 3], d2y = xb[j * 3 + 1], d2z = xb[j * 3 + 2];
		double rx = ax - bx, ry = ay - by, rz = az - bz;
		double b = d1x * d2x + d1y * d2y + d1z * d2z;
		double c = d1x * rx + d1y * ry + d1z * rz;
		double f = d2x * rx + d2y * ry + d2z * rz;
		double denom = 1.0 - b * b;
		double s = denom > 1.0e-9 ? (b * f - c) / denom : 0.0;
		double t = denom > 1.0e-9 ? (f - b * c) / denom : 0.0;
		s = Math.max(-ha[i], Math.min(ha[i], s));
		t = Math.max(-hb[j], Math.min(hb[j], t));
		pa[0] = ax + d1x * s;
		pa[1] = ay + d1y * s;
		pa[2] = az + d1z * s;
		pb[0] = bx + d2x * t;
		pb[1] = by + d2y * t;
		pb[2] = bz + d2z * t;
		depth[0] = sep;
		count = 1;
	}
}
