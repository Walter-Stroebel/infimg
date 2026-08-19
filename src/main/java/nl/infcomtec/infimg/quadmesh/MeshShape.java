package nl.infcomtec.infimg.quadmesh;

/*
 *  Copyright (c) 2022 by Walter Stroebel and InfComTec.
 */

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Walter Stroebel
 */
public abstract class MeshShape implements Shape {

    private Object userObject;

    /**
     * Constructor.
     *
     * @param userObject May be null.
     */
    public MeshShape(Object userObject) {
        this.userObject = userObject;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 61 * hash + Objects.hashCode(this.userObject);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MeshShape other = (MeshShape) obj;
        return Objects.equals(this.userObject, other.userObject);
    }

    /**
     * Counts the number of points inside this MeshShape.
     *
     * @return the number of points inside this MeshShape.
     */
    public int countPoints() {
        int ret = 0;
        Rectangle r = getBounds();
        for (int x = r.x; x < r.x + r.width; x++) {
            for (int y = r.y; y < r.y + r.height; y++) {
                if (contains(x, y)) {
                    ret++;
                }
            }
        }
        return ret;
    }

    /**
     * A MeshShape must contain at least 1 point to be valid.
     *
     * @return
     */
    public boolean isValid() {
        Rectangle r = this.getBounds();
        for (int x = r.x; x < r.x + r.width; x++) {
            for (int y = r.y; y < r.y + r.height; y++) {
                if (contains(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns all the (integer) points inside this MeshShape.
     *
     * Use with care, this could get huge!
     *
     * @return All the (integer) points inside this MeshShape.
     */
    public List<Point> getAllPoints() {
        ArrayList<Point> ret = new ArrayList<>();
        Rectangle b = getBounds();
        for (int x = b.x; x < b.x + b.width; x++) {
            for (int y = b.y; y < b.y + b.height; y++) {
                if (contains(x, y)) {
                    ret.add(new Point(x, y));
                }
            }
        }
        return ret;
    }

    /**
     * Draw the MeshShape.
     *
     * @param g Graphics object.
     */
    public final void draw(Graphics2D g) {
        g.draw(this);
    }

    /**
     * Fill the shape with the selected color.
     *
     * @param g Graphics object.
     */
    public final void fill(Graphics2D g) {
        g.fill(this);
    }

    /**
     * @return the userObject
     */
    public Object getUserObject() {
        return userObject;
    }

    /**
     * @param userObject the userObject to set
     */
    public void setUserObject(Object userObject) {
        this.userObject = userObject;
    }

    public MeshShape merge(MeshShape ms2) {
        return new MergedMeshShape(this, ms2);
    }

    /**
     * Used to reduce meshes.
     */
    public static class MergedMeshShape extends MeshShape {

        @Override
        public String toString() {
            return "MergedMeshShape{" + "bounds=" + bounds + '}';
        }
        private final Rectangle bounds;
        private final Path2D.Double path;

        /**
         * Merges two MeshShapes.
         *
         * @param ms1 will store any userObject in the result.
         * @param ms2 will eventually be sacrified to the great GC, including
         * its children and userObjects.
         */
        public MergedMeshShape(MeshShape ms1, MeshShape ms2) {
            super(ms1.userObject);
            path = new Path2D.Double();
            path.append(ms1.getPathIterator(null), false);
            path.append(ms2.getPathIterator(null), true);
            bounds = path.getBounds();
        }

        @Override
        public Rectangle getBounds() {
            return bounds;
        }

        @Override
        public Rectangle2D getBounds2D() {
            return bounds;
        }

        @Override
        public boolean contains(double d, double d1) {
            return bounds.contains(d, d1);
        }

        @Override
        public boolean contains(Point2D pd) {
            return bounds.contains(pd);
        }

        @Override
        public boolean intersects(double x, double y, double w, double h) {
            return bounds.intersects(x, y, w, h);
        }

        @Override
        public boolean intersects(Rectangle2D rd) {
            return bounds.intersects(rd);
        }

        @Override
        public boolean contains(double x, double y, double w, double h) {
            return bounds.contains(x, y, w, h);
        }

        @Override
        public boolean contains(Rectangle2D rd) {
            return bounds.contains(rd);
        }

        @Override
        public PathIterator getPathIterator(AffineTransform at) {
            return path.getPathIterator(at);
        }

        @Override
        public PathIterator getPathIterator(AffineTransform at, double d) {
            return path.getPathIterator(at, d);
        }
    }

}
