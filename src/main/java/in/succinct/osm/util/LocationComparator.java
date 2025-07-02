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
        if (doc1 == null || Float.isNaN(doc1.score )){
            o1.setScore(0.0F);
        }else if (o1.getScore() == null){
            o1.setScore(doc1.score* 10000);
        }
        ScoreDoc doc2 = o2.getTxnProperty("scoreDoc");
        if (doc2 == null || Float.isNaN(doc2.score )){
            o2.setScore(0.0F);
        }else if (o2.getScore() == null){
            o2.setScore(doc2.score* 10000);
        }
        
        int ret = 0 ;
        ret = (int)(o2.getScore() - o1.getScore()) * 100;
        ret = ret + (int)(o1.getDistance() - o2.getDistance()) * 10;
        ret = ret + (int)(o2.getId() - o1.getId());
        return ret;
    }
}