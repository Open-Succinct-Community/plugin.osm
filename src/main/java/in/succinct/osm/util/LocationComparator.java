package in.succinct.osm.util;

import com.venky.geo.GeoCoordinate;
import in.succinct.osm.db.model.Location;
import org.apache.lucene.search.ScoreDoc;

import java.util.Comparator;

public class LocationComparator implements Comparator<Location> {
    GeoCoordinate center = null;
    public LocationComparator( GeoCoordinate center){
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
        
        int ret = 0 ;
        if (doc1 != null && doc2 != null) {
            ret = (int) ((doc2.score - doc1.score) * 10000);
        }
        if (ret ==0 ) {
            ret = (int)(o1.getDistance() - o2.getDistance());
        }
        if (ret == 0){
            ret = (int)(o2.getId() - o1.getId());
        }
        return ret;
    }
}