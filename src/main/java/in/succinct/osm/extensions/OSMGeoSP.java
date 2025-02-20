package in.succinct.osm.extensions;

import com.venky.cache.UnboundedCache;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectHolder;
import com.venky.geo.GeoCoder;
import com.venky.geo.GeoCoder.GeoAddress;
import com.venky.geo.GeoCoder.GeoSP;
import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.path.Path;
import com.venky.swf.path._IPath;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import com.venky.swf.plugins.lucene.index.common.ModelCollector;
import com.venky.swf.plugins.lucene.index.common.ResultCollector;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Select;
import com.venky.swf.views.View;
import in.succinct.osm.db.model.Location;
import in.succinct.osm.util.Circle;
import in.succinct.osm.util.LocationComparator;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.bouncycastle.math.raw.Mod;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

public class OSMGeoSP implements GeoSP {
    static {
        GeoCoder.getInstance().registerFirstGeoSP(new OSMGeoSP());
    }
    private Circle getCircle(Map<String,String> params){
        Path iPath = Database.getInstance().getContext(_IPath.class.getName());
        if (iPath != null){
            params.put("Lat",iPath.getHeader("Lat"));
            params.put("Lng",iPath.getHeader("Lng"));
            params.put("radius",iPath.getHeader("radius"));
        }
        return new Circle(params);
    }
    @Override
    public GeoLocation getLocation(String address, Map<String, String> params) {
        
        Circle circle =  getCircle(params);
        GeoCoordinate center = circle.getCenter();
        
        Query query = getQuery(address,params);
        ModelCollector<Location> collector = new ModelCollector<>(Location.class,1,2,500,null,
                center == null ? null : new LocationComparator(center)){
            @Override
            protected void addRecord(List<Location> records, Location record) {
                super.addRecord(records, record);
                if (center != null ){
                    record.setDistance(center.distanceTo(new GeoCoordinate(record)) * 1000);
                }
            }
        };
        
        LuceneIndexer.instance(Location.class).fire(query,1,collector);
        List<Location> records = collector.getRecords();
        if (records.isEmpty()){
            return null;
        }else {
            return records.get(0);
        }
    }
    
    @Override
    public GeoAddress getAddress(GeoLocation geoLocation, Map<String, String> params) {
        List<GeoAddress> addresses =  getAddresses(geoLocation,params);
        return addresses.get(0);
    }
    public List<GeoAddress> getAddresses(GeoLocation geoLocation, Map<String, String> params) {
        return getLocations(geoLocation,params).stream().map((l)->new GeoAddress(){
            @Override
            public String getAddress() {
                return StringUtil.valueOf(l.getText());
            }
        }).collect(Collectors.toList());
    }
    
    public List<Location> getLocations(GeoLocation geoLocation, Map<String, String> params) {
        if (geoLocation == null){
            throw new RuntimeException("Cannot reverse lookup");
        }
        GeoCoordinate center = new GeoCoordinate(geoLocation);
        
        ModelReflector<Location> reflector = getReflector();
        TypeConverter<BigDecimal> tc = reflector.getJdbcTypeHelper().getTypeRef(BigDecimal.class).getTypeConverter();
        
        Query query = LatLonPoint.newDistanceQuery("_GEO_LOCATION_", center.getLat().doubleValue(), center.getLng().doubleValue(),
                tc.valueOf(params.getOrDefault("radius",".2")).doubleValue() * 1000) ;
        
        
        ModelCollector<Location> collector = new ModelCollector<>(Location.class, 50,0,500,null,new LocationComparator(center)){
            @Override
            public void collect(Document d, ScoreDoc scoreDoc) {
                String text = d.getField("TEXT").stringValue();
                if (!text.contains("digitalglobe")){
                    super.collect(d, scoreDoc);
                }
            }
            
            @Override
            protected void addRecord(List<Location> records, Location record) {
                record.setDistance(center.distanceTo(new GeoCoordinate(record)));
                super.addRecord(records, record);
            }
            
            @Override
            public boolean isEnough() {
                return super.isEnough() ;
            }
        };
        LuceneIndexer.instance(getReflector()).fire(query,collector.getBatchSize(),collector);
        return  collector.getRecords();
    }
    
    @Override
    public boolean isEnabled(Map<String, String> params) {
        return true;
    }
    
    public ModelReflector<Location> getReflector(){
        return ModelReflector.instance(Location.class);
    }
    
    
    
    
    protected Query getQuery(String strQuery, Map<String, String> params){
        String field = "TEXT";
        List<String> values = new ArrayList<>();
        for (StringTokenizer tk = new StringTokenizer(strQuery,", ()\t\r\n\f"); tk.hasMoreTokens();) {
            String token = tk.nextToken();
           values.add(token);
        }
        try (StandardAnalyzer analyzer = new StandardAnalyzer()) {
            Builder builder = new BooleanQuery.Builder();
            
            Bucket numTerms = new Bucket();
            for (int i = 0 ; i < values.size() ; i ++){
                String value = values.get(i);
                
                Term term = new Term(field, analyzer.normalize(field, value));
                float boost = boostTable.get(values.size()-i);
                
                numTerms.increment();
                addQueries(builder, term, boost);
                //BoostQuery boostQuery = new BoostQuery(new TermQuery(term),boostTable.get(values.size()-i));
            }
            builder.setMinimumNumberShouldMatch((int)Math.ceil(0.6 * numTerms.doubleValue()));
            finalizeQuery(builder,params);
            return builder.build();
        }
    }
    
    protected void finalizeQuery(Builder builder, Map<String, String> params) {
        Circle circle = new Circle(params);
        if (circle.getCenter() != null) {
            Query llq = LatLonPoint.newDistanceQuery("_GEO_LOCATION_",circle.getCenter().getLat().doubleValue(), circle.getCenter().getLng().doubleValue(), circle.getDistance());
            builder.add(llq, Occur.MUST);
        }
        
    }
    
    public void addQueries(Builder builder , Term term ,float boost){
        if (term.text().length() > 7) {
            builder.add(new BoostQuery(new ConstantScoreQuery(new FuzzyQuery(term)), boost), Occur.SHOULD);
        }else {
            builder.add(new BoostQuery(new ConstantScoreQuery(new PrefixQuery(term)),boost),Occur.SHOULD);
        }
    }
    static Map<Integer,Float> boostTable = new UnboundedCache<>() {
        
        
        @Override
        protected Float getValue(Integer key) {
            return Double.valueOf(Math.pow(1.2,key)).floatValue();
        }
    };
}
