package in.succinct.osm.util;

import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.path.Path;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class Circle {
    Map<String,String> params;
    
    public Map<String, String> getParams() {
        return params;
    }
    private final TypeConverter<BigDecimal> tc = Database.getJdbcTypeHelper("").getTypeRef(BigDecimal.class).getTypeConverter();
    
    public Circle(Map<String, String> params){
        this.params = (params != null ? params : new HashMap<>() );
    }
    
    
    GeoCoordinate center = null;
    Double distance = null;
    public GeoCoordinate getCenter() {
        if (this.center == null) {
            String lat = params.get("Lat");
            String lng = params.get("Lng");
            if (!ObjectUtil.isVoid(lat) && !ObjectUtil.isVoid(lng)) {
                center = new GeoCoordinate(tc.valueOf(lat),tc.valueOf(lng));
            }
        }
        
        return center;
    }
    
    public double getDistance() {
        if (distance == null){
            String radius = params.get(  "radius");
            radius = radius == null ? "5" : radius;
            distance= tc.valueOf(radius).doubleValue() * 1000;
        }
        return distance;
    }
}
