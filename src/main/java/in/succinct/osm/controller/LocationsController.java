package in.succinct.osm.controller;

import com.venky.geo.GeoCoder.GeoAddress;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.lucene.index.common.ModelCollector;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.osm.db.model.Location;
import in.succinct.osm.extensions.OSMGeoSP;
import in.succinct.osm.util.Circle;
import in.succinct.osm.util.LocationComparator;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;
import org.json.simple.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public View reverse() {
        Circle circle = getCircle();
        List<Location> locations = new OSMGeoSP().getLocations(circle.getCenter(),circle.getParams());
        return IntegrationAdaptor.instance(getModelClass(), JSONObject.class).createResponse(getPath(),locations,List.of("TEXT","LAT","LNG","DISTANCE"));
    }

    
    @Override
    protected ModelCollector<Location> createCollector(int maxRecords, int minDistinctScores) {
        GeoCoordinate center  = getCircle().getCenter();
        return new ModelCollector<>(Location.class,maxRecords,minDistinctScores,500,getWhereClause(),center == null ? null : new LocationComparator(center)){
            @Override
            protected void addRecord(List<Location> records, Location record) {
                if (center != null) {
                    record.setDistance(center.distanceTo(new GeoCoordinate(record))*1000);
                }
                super.addRecord(records, record);
            }
        };
    }
    
    private Circle circle  = null;
    private Circle getCircle() {
        if (circle == null){
            Path iPath = getPath();
            if (iPath != null){
                Map<String,String> params = new HashMap<>();
                params.put("Lat",iPath.getHeader("Lat"));
                params.put("Lng",iPath.getHeader("Lng"));
                params.put("radius",iPath.getHeader("radius"));
                circle = new Circle(params);
            }
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
