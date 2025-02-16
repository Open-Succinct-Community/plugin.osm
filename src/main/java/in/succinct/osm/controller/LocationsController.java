package in.succinct.osm.controller;

import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.lucene.index.common.ResultCollector;
import com.venky.swf.views.View;
import in.succinct.osm.db.model.Location;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public class LocationsController extends ModelController<Location> {
    public LocationsController(Path path) {
        super(path);
    }
    
    @Override
    @RequireLogin(false)
    public View index() {
        return super.index();
    }
    
    @Override
    @RequireLogin(false)
    public View search() {
        return super.search();
    }
    
    @Override
    @RequireLogin(false)
    public View search(String strQuery) {
        return super.search(strQuery);
    }
    
    @RequireLogin(false)
    public View reverse(){
        String lat = getPath().getHeader("Lat");
        String lng = getPath().getHeader(  "Lng");
        String radius = getPath().getHeader("radius");
        
        ModelReflector<Location> reflector = getReflector();
        TypeConverter<BigDecimal> tc = reflector.getJdbcTypeHelper().getTypeRef(BigDecimal.class).getTypeConverter();
        
        if (!reflector.isVoid(lat) && !reflector.isVoid(lng) && reflector.getIndexedFields().contains("LAT")) {
            GeoCoordinate center = new GeoCoordinate(tc.valueOf(lat),tc.valueOf(lng));
            
            Query query = LatLonPoint.newDistanceQuery("_GEO_LOCATION_", tc.valueOf(lat).doubleValue(), tc.valueOf(lng).doubleValue(),
                    tc.valueOf(radius == null ? 1000 : radius).doubleValue());
            
            ObjectHolder<Location> closest = new ObjectHolder<>(null);
            getIndexer().fire(query, 1, new ResultCollector() {
                @Override
                public void collect(Document doc, ScoreDoc scoreDoc) {
                    Location current  = Database.getTable(Location.class).newRecord();
                    setLocation(current,doc);
                    Location old = closest.get();
                    double old_distance = old == null ? Double.POSITIVE_INFINITY : old.getTxnProperty("distance");
                    double current_distance = current.getTxnProperty("distance");
                    if (current_distance < old_distance){
                        closest.set(current);
                    }
                }
                private void setLocation(Location location, Document doc){
                    TypeConverter<BigDecimal> converter = getReflector().getJdbcTypeHelper().getTypeRef(BigDecimal.class).getTypeConverter();
                    BigDecimal lat  = converter.valueOf(doc.getField("LAT").stringValue());
                    BigDecimal lng = converter.valueOf(doc.getField("LNG").stringValue());
                    location.setLat(lat);
                    location.setLng(lng);
                    location.setId(Long.parseLong(doc.getField("ID").stringValue()));
                    location.setTxnProperty("distance",new GeoCoordinate(location).distanceTo(center));
                }
                
                @Override
                public int count() {
                    return 0;
                }
            });
            if (closest.get() != null) {
                Location location = Database.getTable(Location.class).getRefreshed(closest.get());
                return show(location);
            }
        }
        throw new RuntimeException("Cannot reverse lookup");
    }
    
    @Override
    protected List<Location> searchRecords(Query q, int maxRecords) {
        Circle circle = getCircle();
        GeoCoordinate center = circle.getCenter();
        
        List<Location> locations = super.searchRecords(q, maxRecords , center == null ? 0 : 2 );
        if (center != null && circle.getDistance() > 0) {
            if (locations.size() > 1) {
                locations.sort(new LocationComparator(center));
            }else  {
                for (Location location : locations) {
                    location.setDistance(center.distanceTo(new GeoCoordinate(location)) * 1000);
                }
            }
        }
        if (maxRecords > 0 && locations.size() > maxRecords) {
            locations = locations.subList(0,maxRecords);
        }
        return locations;
    }
    
    private static class LocationComparator implements Comparator<Location> {
        GeoCoordinate center = null;
        LocationComparator( GeoCoordinate center){
            this.center = center;
        }
        
        @Override
        public int compare(Location o1, Location o2) {
            if (o1.getDistance() == null) {
                o1.setDistance(center.distanceTo(new GeoCoordinate(o1)) * 1000);
            }
            if (o2.getDistance() == null) {
                o2.setDistance(center.distanceTo(new GeoCoordinate(o2)) * 1000);
            }
            ScoreDoc doc1 = o1.getTxnProperty("scoreDoc");
            ScoreDoc doc2 = o2.getTxnProperty("scoreDoc");
            int ret = (int)((doc2.score - doc1.score)*10000);
            if (ret ==0 ) {
                ret = (int)(o1.getDistance() - o2.getDistance());
            }
            if (ret == 0){
                ret = (int)(o2.getId() - o1.getId());
            }
            return ret;
        }
    }
    
    private static class Circle {
        GeoCoordinate center = null;
        double distance = 0;
        public Circle(Path path){
            String lat = path.getHeader("Lat");
            String lng = path.getHeader(  "Lng");
            String radius = path.getHeader(  "radius");
            radius = radius == null ? "5" : radius;
            
            TypeConverter<BigDecimal> tc = Database.getJdbcTypeHelper("").getTypeRef(BigDecimal.class).getTypeConverter();
            if (!ObjectUtil.isVoid(lat)  && !ObjectUtil.isVoid(lng)) {
                center = new GeoCoordinate(tc.valueOf(lat),tc.valueOf(lng));
                distance = tc.valueOf(radius).doubleValue() * 1000;
            }
        }
        
        public GeoCoordinate getCenter() {
            return center;
        }
        
        public double getDistance() {
            return distance;
        }
    }
    
    
    private Circle circle  = null;
    private Circle getCircle() {
        if (circle == null){
            circle = new Circle(getPath());
        }
        return circle;
    }
    
    @Override
    protected void finalizeQuery(Builder builder) {
        super.finalizeQuery(builder);
        Circle circle = getCircle();
        if (circle.getCenter() != null) {
            Query query = LatLonPoint.newDistanceQuery("_GEO_LOCATION_",circle.getCenter().getLat().doubleValue(), circle.getCenter().getLng().doubleValue(), circle.getDistance());
            builder.add(query, Occur.MUST);
        }
    }
}
