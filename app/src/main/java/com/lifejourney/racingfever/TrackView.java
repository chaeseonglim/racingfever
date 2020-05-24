package com.lifejourney.racingfever;

import com.lifejourney.engine2d.Engine2D;
import com.lifejourney.engine2d.Point;
import com.lifejourney.engine2d.PointF;
import com.lifejourney.engine2d.Rect;
import com.lifejourney.engine2d.RectF;
import com.lifejourney.engine2d.Size;
import com.lifejourney.engine2d.Sprite;
import com.lifejourney.engine2d.Vector2D;
import com.lifejourney.engine2d.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

class TrackView implements View {

    private String LOG_TAG = "TrackView";

    TrackView(TrackData data, float scale) {
        this.data = data;
        this.sprites = new HashMap<CoordKey, Sprite>();
        this.tileSize = new Size((int) (TILE_WIDTH*scale), (int) (TILE_HEIGHT*scale));
        update();
    }

    /**
     *
     * @return
     */
    private Rect getCachedRegion() {
        Rect cachedRegion = new Rect(Engine2D.GetInstance().getViewport());

        // Adding gaps to viewport for caching more sprites around
        cachedRegion.x = Math.max(0, cachedRegion.x - tileSize.width *2);
        cachedRegion.width += tileSize.width * 4;
        cachedRegion.y = Math.max(0, cachedRegion.y - tileSize.height *2);
        cachedRegion.height += tileSize.height * 4;

        return cachedRegion;
    }

    /**
     *
     */
    private void cleanupUnusedSprites() {
        Rect cachedRegion = getCachedRegion();

        Iterator<HashMap.Entry<CoordKey, Sprite>> iter = sprites.entrySet().iterator();
        while (iter.hasNext()) {
            HashMap.Entry<CoordKey, Sprite> entry = iter.next();
            CoordKey key = entry.getKey();
            Rect spriteRect = new Rect(key.getX()* tileSize.width, key.getY()* tileSize.height,
                    tileSize.width, tileSize.height);
            if (!Rect.intersects(cachedRegion, spriteRect)) {
                Sprite sprite = entry.getValue();
                sprite.close();
                iter.remove();
            }
        }
    }

    /**
     *
     */
    @Override
    public void update() {
        if (!visible)
            return;

        // clean up unused spries
        cleanupUnusedSprites();

        // build up sprites
        Rect cachedRegion = getCachedRegion();
        Size trackDataSize = data.getSize();

        for (int y = cachedRegion.top() / tileSize.height;
             y < Math.min(cachedRegion.bottom() / tileSize.height, trackDataSize.height);
             ++y) {
            for (int x = cachedRegion.left() / tileSize.width;
                 x < Math.min(cachedRegion.right() / tileSize.width, trackDataSize.width);
                 ++x) {
                if (sprites.get(new CoordKey(x, y)) != null)
                    continue;

                Point textureGrid;

                TrackData.TileType tileType = data.getTileType(new Point(x, y));
                TrackData.TileType leftTileType = data.getTileType(new Point(x - 1, y));
                TrackData.TileType rightTileType = data.getTileType(new Point(x + 1, y));
                TrackData.TileType upTileType = data.getTileType(new Point(x, y - 1));
                TrackData.TileType downTileType = data.getTileType(new Point(x, y + 1));

                switch (tileType) {
                    case GRASS:
                    case UNKNOWN:
                        if (leftTileType.movable() && upTileType.movable() && rightTileType.movable()) {
                            textureGrid = new Point(2, 5);
                        }
                        else if (leftTileType.movable() && downTileType.movable() && rightTileType.movable()) {
                            textureGrid = new Point(0, 5);
                        }
                        else if (leftTileType.movable() && downTileType.movable() && upTileType.movable()) {
                            textureGrid = new Point(3, 5);
                        }
                        else if (rightTileType.movable() && downTileType.movable() && upTileType.movable()) {
                            textureGrid = new Point(1, 5);
                        }
                        else if (leftTileType.movable() && upTileType.movable()) {
                            textureGrid = new Point(3, 3);
                        }
                        else if (leftTileType.movable() && downTileType.movable()) {
                            textureGrid = new Point(0, 3);
                        }
                        else if (rightTileType.movable() && upTileType.movable()) {
                            textureGrid = new Point(2, 3);
                        }
                        else if (rightTileType.movable() && downTileType.movable()) {
                            textureGrid = new Point(1, 3);
                        }
                        else {
                            textureGrid = new Point(1, 0);
                        }
                        break;

                    case START:
                        textureGrid = new Point(0, 0);
                        break;

                    case END:
                        textureGrid = new Point(0, 0);
                        break;

                    case ROAD:
                        if (!leftTileType.movable() && !upTileType.movable() && !rightTileType.movable()) {
                            textureGrid = new Point(2, 4);
                        }
                        else if (!leftTileType.movable() && !downTileType.movable() && !rightTileType.movable()) {
                            textureGrid = new Point(0, 4);
                        }
                        else if (!leftTileType.movable() && !downTileType.movable() && !upTileType.movable()) {
                            textureGrid = new Point(3, 4);
                        }
                        else if (!rightTileType.movable() && !downTileType.movable() && !upTileType.movable()) {
                            textureGrid = new Point(1, 4);
                        }
                        else if (!leftTileType.movable() && !upTileType.movable()) {
                            textureGrid = new Point(3, 2);
                        }
                        else if (!leftTileType.movable() && !downTileType.movable()) {
                            textureGrid = new Point(0, 2);
                        }
                        else if (!rightTileType.movable() && !upTileType.movable()) {
                            textureGrid = new Point(2, 2);
                        }
                        else if (!rightTileType.movable() && !downTileType.movable()) {
                            textureGrid = new Point(1, 2);
                        }
                        else {
                            textureGrid = new Point(0, 0);
                        }
                        break;

                    case FINISH:
                        textureGrid = new Point(0, 0);
                        break;

                    default:
                        textureGrid = new Point(1, 0);
                        break;
                }

                Sprite.Builder spriteBuilder =
                    new Sprite.Builder("map_tile.png")
                            .position(new Point(
                                    x * tileSize.width + tileSize.width /2,
                                    y * tileSize.height + tileSize.height /2))
                            .size(new Size(tileSize.width, tileSize.height))
                            .gridSize(new Size(4, 6))
                            .layer(MAP_LAYER).visible(true);
                Sprite sprite = spriteBuilder.build();
                sprite.setGridIndex(textureGrid);
                sprites.put(new CoordKey(x, y), sprite);
            }
        }
    }

    /**
     *
     */
    @Override
    public void commit() {
        if (!visible)
            return;

        for (HashMap.Entry<CoordKey, Sprite> entry : sprites.entrySet()) {
            entry.getValue().commit();
        }
    }

    /**
     *
     */
    @Override
    public void show() {
        visible = true;
        for (HashMap.Entry<CoordKey, Sprite> entry : sprites.entrySet()) {
            entry.getValue().show();
        }
    }

    /**
     *
     */
    @Override
    public void hide() {
        visible = false;
        for (HashMap.Entry<CoordKey, Sprite> entry : sprites.entrySet()) {
            entry.getValue().hide();
        }
    }

    /**
     *
     * @return
     */
    @Override
    public Size getSize() {
        return new Size(data.getSize()).multiply(tileSize.width, tileSize.height);
    }

    /**
     *
     * @param pt
     * @return
     */
    RectF getScreenRegionfromTrackCoord(Point pt) {
        return new RectF(pt.x*tileSize.width, pt.y*tileSize.height,
                tileSize.width, tileSize.height);
    }

    /**
     *
     * @param pt
     * @return
     */
    Point getTrackCoordFromScreenCoord(PointF pt) {
        return new Point(pt).divide(new Point(tileSize.width, tileSize.height));
    }

    /**
     *
     * @param start
     * @param direction
     * @param maxDistance
     * @return
     */
    ArrayList<Point> getRaytracedTileList(PointF start, float direction, float maxDistance) {
        // Find end point first
        Vector2D endVector =
                new Vector2D(direction).multiply(maxDistance).add(start.vectorize());
        PointF end = new PointF(endVector);

        return getRaytracedTileList(start, end);
    }

    /**
     *
     * @param start
     * @param end
     * @return
     */
    ArrayList<Point> getRaytracedTileList(PointF start, PointF end) {
        PointF startpoint = new PointF(start).divide(new PointF(tileSize.width, tileSize.height));
        PointF endpoint = new PointF(end).divide(new PointF(tileSize.width, tileSize.height));

        // Raytracing code
        // http://playtechs.blogspot.com/2007/03/raytracing-on-grid.html
        float dx = Math.abs(endpoint.x - startpoint.x);
        float dy = Math.abs(endpoint.y - startpoint.y);

        int x = (int)(Math.floor(startpoint.x));
        int y = (int)(Math.floor(startpoint.y));

        float dt_dx = 1.0f / dx;
        float dt_dy = 1.0f / dy;

        float t = 0;

        int n = 1;
        int x_inc, y_inc;
        float t_next_vertical, t_next_horizontal;

        if (dx == 0) {
            x_inc = 0;
            t_next_horizontal = dt_dx; // infinity
        }
        else if (endpoint.x > startpoint.x) {
            x_inc = 1;
            n += (int)(Math.floor(endpoint.x)) - x;
            t_next_horizontal = (float) (Math.floor(startpoint.x) + 1 - startpoint.x) * dt_dx;
        }
        else {
            x_inc = -1;
            n += x - (int)(Math.floor(endpoint.x));
            t_next_horizontal = (float) (startpoint.x - Math.floor(startpoint.x)) * dt_dx;
        }

        if (dy == 0) {
            y_inc = 0;
            t_next_vertical = dt_dy; // infinity
        }
        else if (endpoint.y > startpoint.y) {
            y_inc = 1;
            n += (int) (Math.floor(endpoint.y)) - y;
            t_next_vertical = (float) (Math.floor(startpoint.y) + 1 - startpoint.y) * dt_dy;
        }
        else {
            y_inc = -1;
            n += y - (int)(Math.floor(endpoint.y));
            t_next_vertical = (float) (startpoint.y - Math.floor(startpoint.y)) * dt_dy;
        }

        ArrayList<Point> points = new ArrayList<>();
        for (; n > 0; --n) {
            if (x < 0 || y < 0 ||
                    x >= data.getSize().width ||
                    y >= data.getSize().height) {
                continue;
            }

            // x, y
            points.add(new Point(x, y));

            if (t_next_vertical < t_next_horizontal) {
                y += y_inc;
                t = t_next_vertical;
                t_next_vertical += dt_dy;
            }
            else {
                x += x_inc;
                t = t_next_horizontal;
                t_next_horizontal += dt_dx;
            }
        }

        return points;
    }

    /**
     *
     * @return
     */
    Size getTileSize() {
        return tileSize;
    }

    private final int MAP_LAYER = 0;
    private final int TILE_WIDTH = 32, TILE_HEIGHT = 32;

    private TrackData data;
    private HashMap<CoordKey, Sprite> sprites;
    private boolean visible;
    private Size tileSize;
}
