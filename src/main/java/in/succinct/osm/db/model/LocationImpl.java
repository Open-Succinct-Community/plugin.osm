package in.succinct.osm.db.model;

import com.venky.swf.db.table.ModelImpl;

public class LocationImpl extends ModelImpl<Location> {
    public LocationImpl(Location location){
        super(location);
    }
    
    Double distance;
    public Double getDistance(){
        return this.distance;
    }
    public void setDistance(Double distance){
        this.distance = distance;
    }
}
