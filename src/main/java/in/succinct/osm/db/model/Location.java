package in.succinct.osm.db.model;

import com.venky.geo.GeoLocation;
import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_NAME;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.model.Model;

import java.math.BigDecimal;

public interface Location extends Model , GeoLocation {
    @COLUMN_DEF(StandardDefault.ZERO)
    long getOsmId();
    void setOsmId(long osmId);
    
    @COLUMN_SIZE(1)
    String getOsmType();
    void setOsmType(String osmType);
    
    @Index
    @COLUMN_SIZE(8192)
    String getText();
    void setText(String text);
    
    @Index
    public BigDecimal getLat();
    
    @Index
    BigDecimal getLng();
    
    @IS_VIRTUAL
    Double getDistance();
    void setDistance(Double distance);
}
