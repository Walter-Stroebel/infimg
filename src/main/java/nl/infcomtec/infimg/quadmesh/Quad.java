package nl.infcomtec.infimg.quadmesh;


import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Holds a leaf or node of a Quad tree.
 *
 * @author walter
 */
public class Quad extends MeshShape {

    /**
     * Extra information set by some ShapeCriteria when the condition
     * is met, else for instance Boolean.TRUE.
     */
    public Object userObject;

    /**
     * Parent of this Quad, null if root node.
     */
    public final Quad parent;
    /**
     * Area described by this Quad.
     */
    public final Rectangle rect;
    private final boolean leaf;
    /**
     * Sub-quads if not a leaf node, null otherwise.
     */
    public Quad[] nodes;
    /**
     * Number of leafs in this branch.
     */
    public int leafCount;

    /**
     * Parse the image into Quad leaves using the passed criterion.
     *
     * @param bi Image to examine.
     * @param area Area of the image to examine.
     * @param shapeCriterion Criterion to use.
     */
    public Quad(ImageSource bi, Rectangle area, ShapeCriterion shapeCriterion) {
        this(null, bi, area, shapeCriterion);
    }

    /**
     * Parse the image into Quad leaves using the passed criterion.
     *
     * @param parent Parent of this Quad.
     * @param bi Image to examine.
     * @param area Area of the image to examine.
     * @param shapeCriterion Criterion to use.
     */
    private Quad(Quad parent, ImageSource bi, Rectangle area, ShapeCriterion shapeCriterion) {
        super(null);
        this.parent = parent;
        if (null == area) {
            this.rect = new Rectangle(bi.getWidth(), bi.getHeight());
        } else {
            this.rect = area;
        }
        Rectangle _area = this.rect.getBounds();
        leafCount = 0;
        userObject = shapeCriterion.criterionMet(bi, _area);
        if (null==userObject) {
            if (_area.width > 1) {
                if (_area.height > 1) {
                    incLeaves(4);
                    leaf = false;
                    int mw = _area.width / 2;
                    int mh = _area.height / 2;
                    nodes = new Quad[4];
                    nodes[0] = new Quad(this, bi, new Rectangle(_area.x, _area.y, mw, mh), shapeCriterion);
                    nodes[1] = new Quad(this, bi, new Rectangle(_area.x + mw, _area.y, _area.width - mw, mh), shapeCriterion);
                    nodes[2] = new Quad(this, bi, new Rectangle(_area.x, _area.y + mh, mw, _area.height - mh), shapeCriterion);
                    nodes[3] = new Quad(this, bi, new Rectangle(_area.x + mw, _area.y + mh, _area.width - mw, _area.height - mh), shapeCriterion);
                } else {
                    incLeaves(2);
                    leaf = false;
                    int mw = _area.width / 2;
                    int mh = _area.height;
                    nodes = new Quad[2];
                    nodes[0] = new Quad(this, bi, new Rectangle(_area.x, _area.y, mw, mh), shapeCriterion);
                    nodes[1] = new Quad(this, bi, new Rectangle(_area.x + mw, _area.y, _area.width - mw, mh), shapeCriterion);
                }
            } else if (_area.height > 1) {
                incLeaves(2);
                leaf = false;
                int mw = _area.width;
                int mh = _area.height / 2;
                nodes = new Quad[2];
                nodes[0] = new Quad(this, bi, new Rectangle(_area.x, _area.y, mw, mh), shapeCriterion);
                nodes[1] = new Quad(this, bi, new Rectangle(_area.x, _area.y + mh, mw, _area.height - mh), shapeCriterion);
            } else {
                leaf = true;
                nodes = null;
            }
        } else {
            leaf = true;
            nodes = null;
            setUserObject(userObject);
        }
    }

    /**
     * Parse the image into Quad leaves using the passed criterion.
     *
     * @param bi Image to examine.
     * @param shapeCriterion Criterion to use.
     */
    public Quad(ImageSource bi, ShapeCriterion shapeCriterion) {
        this(bi, new Rectangle(bi.getWidth(), bi.getHeight()), shapeCriterion);
    }

    @Override
    public boolean contains(double d, double d1) {
        return rect.contains(d, d1);
    }

    @Override
    public boolean contains(Point2D pd) {
        return rect.contains(pd);
    }

    @Override
    public boolean contains(double d, double d1, double d2, double d3) {
        return rect.contains(d, d1, d2, d3);
    }

    @Override
    public boolean contains(Rectangle2D rd) {
        return rect.contains(rd);
    }

    @Override
    public Rectangle getBounds() {
        return rect.getBounds();
    }

    @Override
    public Rectangle2D getBounds2D() {
        return rect.getBounds2D();
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at) {
        return rect.getPathIterator(at);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at, double d) {
        return rect.getPathIterator(at, d);
    }

    private void incLeaves(int i) {
        leafCount += i;
        if (parent != null) {
            parent.incLeaves(i);
        }
    }

    /**
     * Find the widest Quad.
     *
     * @param w Initially 0.
     * @return Widest quad or null if there are no quads wider then w.
     */
    public Quad findWidest(final int w) {
        if (rect.getBounds().width <= w) {
            return null;
        }
        if (leaf) {
            return this;
        }
        Quad ret = null;
        for (Quad node : nodes) {
            if (node.leaf) {
                if (node.rect.getBounds().width > w) {
                    if (ret == null || node.rect.getBounds().width > ret.rect.getBounds().width) {
                        ret = node;
                    }
                }
            } else if (ret == null) {
                ret = node.findWidest(w);
            } else {
                Quad better = node.findWidest(ret.rect.getBounds().width);
                if (better != null) {
                    ret = better;
                }
            }
        }
        return ret;
    }

    /**
     * Find the highest Quad.
     *
     * @param h Initially 0.
     * @return Highest quad or null if there are no quads higher then h.
     */
    public Quad findHighest(final int h) {
        if (rect.getBounds().height <= h) {
            return null;
        }
        if (leaf) {
            return this;
        }
        Quad ret = null;
        for (Quad node : nodes) {
            if (node.leaf) {
                if (node.rect.getBounds().height > h) {
                    if (ret == null || node.rect.getBounds().height > ret.rect.getBounds().height) {
                        ret = node;
                    }
                }
            } else if (ret == null) {
                ret = node.findHighest(h);
            } else {
                Quad better = node.findHighest(ret.rect.getBounds().height);
                if (better != null) {
                    ret = better;
                }
            }
        }
        return ret;
    }

    /**
     * Find the biggest Quad.
     *
     * @param wh Initially 0.
     * @return Biggest quad or null if there are no quads bigger then wh.
     */
    public Quad findBiggest(final int wh) {
        if (rect.getBounds().width * rect.getBounds().height <= wh) {
            return null;
        }
        if (leaf) {
            return this;
        }
        Quad ret = null;
        for (Quad node : nodes) {
            if (node.leaf) {
                if (node.rect.getBounds().width * node.rect.getBounds().height > wh) {
                    if (ret == null || node.rect.getBounds().width * node.rect.getBounds().height > ret.rect.getBounds().width * ret.rect.getBounds().height) {
                        ret = node;
                    }
                }
            } else if (ret == null) {
                ret = node.findHighest(wh);
            } else {
                Quad better = node.findHighest(ret.rect.getBounds().width * ret.rect.getBounds().height);
                if (better != null) {
                    ret = better;
                }
            }
        }
        return ret;
    }

    @Override
    public boolean intersects(double d, double d1, double d2, double d3) {
        return rect.intersects(d, d1, d2, d3);
    }

    @Override
    public boolean intersects(Rectangle2D rd) {
        return rect.intersects(rd);
    }

    /**
     * Traverses the Quad tree and applies the QuadProcessor on each node.
     *
     * @param parm QuadProcessor to use.
     * @return True if ok to continue, false otherwise. Top-level false means
     * the traversal was aborted.
     */
    public boolean traverse(QuadProcessor parm) {
        if (nodes == null) {
            return parm.process(this);
        } else {
            for (Quad node : nodes) {
                if (!node.traverse(parm)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Quad{" + "area=" + rect.getBounds() + ", metObject=" + userObject + ", isLeaf=" + (nodes == null) + ", leafCount=" + leafCount + '}';
    }

    /**
     * Draw all leaves.
     *
     * @param g Draw on this.
     * @param b Draw Met leaves (true) or UnMet (false) leaves;
     */
    public void drawMet(Graphics2D g, boolean b) {
        if (nodes == null) {
            if ((null!=userObject && b) || (null==userObject && !b)) {
                g.draw(rect);
            }
        } else {
            for (Quad node : nodes) {
                node.drawMet(g, b);
            }
        }
    }

    /**
     * Fill all leaves.
     *
     * @param g Draw on this.
     * @param b Fill Met leaves (true) or UnMet (false) leaves;
     */
    public void fillMet(Graphics2D g, boolean b) {
        if (nodes == null) {
            if ((null!=userObject && b) || (null==userObject && !b)) {
                g.fill(rect);
            }
        } else {
            for (Quad node : nodes) {
                node.fillMet(g, b);
            }
        }
    }

    public static interface MergeOperator {

        void merge(Quad q);

        boolean isMerged(Quad q);
    }

    public void merge(MergeOperator merge) {
        if (!merge.isMerged(this) && null != nodes) {
            for (Quad node : nodes) {
                node.merge(merge);
            }
        }
    }
    
    /**
     * Return a shape for the Met or UnMet leaves.
     *
     * Warning: can be extremely slow!
     * 
     * @param b Return Met leaves (true) or UnMet (false) leaves
     * @return An Area object.
     */
    public Area getMet(boolean b) {
        if (nodes == null) {
            if ((null!=userObject && b) || (null==userObject && !b)) {
                return new Area(rect);
            } else {
                return new Area();
            }
        } else {
            Area ret = new Area();
            for (Quad node : nodes) {
                Area s = node.getMet(b);
                if (!s.isEmpty()) {
                    ret.add(s);
                }
            }
            return ret;
        }
    }

    public static abstract class MergeMinSize implements MergeOperator {

        private final int minSize;

        public MergeMinSize(int minSize) {
            this.minSize = minSize;
        }

        @Override
        public boolean isMerged(Quad q) {
            if (null == q.nodes) {
                return true;
            }
            int size = q.rect.getBounds().width * q.rect.getBounds().height;
            if (size < minSize) {
                merge(q);
                q.nodes = null;
                q.leafCount = 1;
                return true;
            }
            return false;
        }
    }
}
