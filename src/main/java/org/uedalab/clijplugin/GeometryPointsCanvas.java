package org.uedalab.clijplugin;

import ij.ImagePlus;
import ij.gui.ImageCanvas;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

class GeometryPointsCanvas extends ImageCanvas {

    interface StateProvider {
        RenderState getRenderState();

        void handleCanvasClick(double x, double y, int z);
    }

    static final class RenderState {
        final List<RenderPoint> points;
        final RenderModel selectedModel;

        RenderState(final List<RenderPoint> points, final RenderModel selectedModel) {
            this.points = points;
            this.selectedModel = selectedModel;
        }
    }

    static final class RenderPoint {
        final double x;
        final double y;
        final int z;
        final boolean highlighted;

        RenderPoint(final double x, final double y, final int z, final boolean highlighted) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.highlighted = highlighted;
        }
    }

    static final class RenderModel {
        final GeometryPointsControlFrame.ModelType type;
        final double cx;
        final double cy;
        final double cz;
        final double vx;
        final double vy;
        final double vz;

        RenderModel(final GeometryPointsControlFrame.ModelType type,
                    final double cx, final double cy, final double cz,
                    final double vx, final double vy, final double vz) {
            this.type = type;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
        }
    }

    private final StateProvider stateProvider;

    GeometryPointsCanvas(final ImagePlus imp, final StateProvider stateProvider) {
        super(imp);
        this.stateProvider = stateProvider;

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                final double x = offScreenXD(e.getX());
                final double y = offScreenYD(e.getY());
                if (!Double.isFinite(x) || !Double.isFinite(y)) {
                    return;
                }
                stateProvider.handleCanvasClick(x, y, getCurrentZ());
            }
        });

        addMouseWheelListener(this::handleMouseWheelZoom);
    }

    @Override
    public void paint(final Graphics g) {
        super.paint(g);
        drawOurAnnotations(g);
    }

    private void drawOurAnnotations(final Graphics g) {
        final RenderState state = stateProvider.getRenderState();
        if (state == null) {
            return;
        }
        final int currentZ = getCurrentZ();
        final Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (RenderPoint p : state.points) {
            if (p.z != currentZ) {
                continue;
            }
            final int sx = screenXD(p.x);
            final int sy = screenYD(p.y);
            final int r = p.highlighted ? 4 : 3;
            g2.setColor(p.highlighted ? Color.RED : Color.BLUE);
            g2.fillOval(sx - r, sy - r, r * 2, r * 2);
        }

        final RenderModel model = state.selectedModel;
        if (model != null) {
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(2f));
            if (model.type == GeometryPointsControlFrame.ModelType.LINE) {
                drawLineModelIntersection(g2, model, currentZ);
            } else {
                drawPlaneModelIntersection(g2, model, currentZ);
            }
        }

        g2.dispose();
    }

    private int getCurrentZ() {
        if (imp == null) {
            return 1;
        }
        if (imp.isHyperStack()) {
            return Math.max(1, imp.getZ());
        }
        if (imp.getNSlices() > 1) {
            return Math.max(1, imp.getCurrentSlice());
        }
        return 1;
    }

    private void drawLineModelIntersection(final Graphics2D g2, final RenderModel model, final int z0) {
        final double eps = 1e-9;
        final int w = imp == null ? 0 : imp.getWidth();
        final int h = imp == null ? 0 : imp.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        if (Math.abs(model.vz) > eps) {
            final double t = (z0 - model.cz) / model.vz;
            final double x = model.cx + t * model.vx;
            final double y = model.cy + t * model.vy;
            if (x < 0 || y < 0 || x > w - 1 || y > h - 1) {
                return;
            }
            final int sx = screenXD(x);
            final int sy = screenYD(y);
            final int r = 5;
            g2.drawLine(sx - r, sy, sx + r, sy);
            g2.drawLine(sx, sy - r, sx, sy + r);
            return;
        }

        if (Math.abs(z0 - Math.round(model.cz)) <= 1.0) {
            final double norm = Math.hypot(model.vx, model.vy);
            if (norm <= eps) {
                final int sx = screenXD(model.cx);
                final int sy = screenYD(model.cy);
                final int r = 5;
                g2.drawLine(sx - r, sy, sx + r, sy);
                g2.drawLine(sx, sy - r, sx, sy + r);
                return;
            }
            final double dx = model.vx / norm;
            final double dy = model.vy / norm;
            final double k = 50.0;
            final double x1 = model.cx - k * dx;
            final double y1 = model.cy - k * dy;
            final double x2 = model.cx + k * dx;
            final double y2 = model.cy + k * dy;
            g2.drawLine(screenXD(x1), screenYD(y1), screenXD(x2), screenYD(y2));
        }
    }

    private void drawPlaneModelIntersection(final Graphics2D g2, final RenderModel model, final int z0) {
        final double eps = 1e-9;
        final int w = imp == null ? 0 : imp.getWidth();
        final int h = imp == null ? 0 : imp.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        final double nx = model.vx;
        final double ny = model.vy;
        final double nz = model.vz;

        if (Math.abs(nx) + Math.abs(ny) <= eps) {
            if (Math.abs(z0 - model.cz) <= 0.5) {
                g2.setColor(new Color(255, 0, 0, 120));
                final int sx = screenXD(model.cx);
                final int sy = screenYD(model.cy);
                g2.drawOval(sx - 6, sy - 6, 12, 12);
            }
            return;
        }

        final double c = nz * (z0 - model.cz) - nx * model.cx - ny * model.cy;
        final List<double[]> pts = new ArrayList<>(4);

        addIntersectionForX(pts, 0, ny, c, h, eps);
        addIntersectionForX(pts, w - 1, ny, c + nx * (w - 1), h, eps);
        addIntersectionForY(pts, 0, nx, c, w, eps);
        addIntersectionForY(pts, h - 1, nx, c + ny * (h - 1), w, eps);

        if (pts.size() < 2) {
            return;
        }

        double[] p1 = pts.get(0);
        double[] p2 = pts.get(1);
        double best = -1.0;
        for (int i = 0; i < pts.size(); i++) {
            for (int j = i + 1; j < pts.size(); j++) {
                final double dx = pts.get(i)[0] - pts.get(j)[0];
                final double dy = pts.get(i)[1] - pts.get(j)[1];
                final double d2 = dx * dx + dy * dy;
                if (d2 > best) {
                    best = d2;
                    p1 = pts.get(i);
                    p2 = pts.get(j);
                }
            }
        }
        g2.drawLine(screenXD(p1[0]), screenYD(p1[1]), screenXD(p2[0]), screenYD(p2[1]));
    }

    private static void addIntersectionForX(final List<double[]> pts, final double x,
                                            final double denomNy, final double valueC,
                                            final int h, final double eps) {
        if (Math.abs(denomNy) <= eps) {
            return;
        }
        final double y = -valueC / denomNy;
        if (y < 0 || y > h - 1) {
            return;
        }
        addUniquePoint(pts, x, y);
    }

    private static void addIntersectionForY(final List<double[]> pts, final double y,
                                            final double denomNx, final double valueC,
                                            final int w, final double eps) {
        if (Math.abs(denomNx) <= eps) {
            return;
        }
        final double x = -valueC / denomNx;
        if (x < 0 || x > w - 1) {
            return;
        }
        addUniquePoint(pts, x, y);
    }

    private static void addUniquePoint(final List<double[]> pts, final double x, final double y) {
        for (double[] p : pts) {
            if (Math.abs(p[0] - x) < 1e-6 && Math.abs(p[1] - y) < 1e-6) {
                return;
            }
        }
        pts.add(new double[]{x, y});
    }

    private void handleMouseWheelZoom(final MouseWheelEvent e) {
        if (!e.isControlDown()) {
            return;
        }
        if (e.getWheelRotation() < 0) {
            zoomIn(e.getX(), e.getY());
        } else {
            zoomOut(e.getX(), e.getY());
        }
        e.consume();
        repaint();
    }
}
