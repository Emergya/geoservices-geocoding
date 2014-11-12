package com.emergya.geoservices.geocoding.portals;

import com.emergya.geoservices.geocoding.portals.antlr4.PortalsLexer;
import com.emergya.geoservices.geocoding.portals.antlr4.PortalsParser;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import net.opengis.gml.v_3_1_1.DirectPositionType;
import net.opengis.gml.v_3_1_1.PointType;
import net.opengis.xls.v_1_2_0.AbstractResponseParametersType;
import net.opengis.xls.v_1_2_0.AddressType;
import net.opengis.xls.v_1_2_0.BuildingLocatorType;
import net.opengis.xls.v_1_2_0.DirectoryRequestType;
import net.opengis.xls.v_1_2_0.DistanceType;
import net.opengis.xls.v_1_2_0.GeocodeRequestType;
import net.opengis.xls.v_1_2_0.GeocodeResponseListType;
import net.opengis.xls.v_1_2_0.GeocodeResponseType;
import net.opengis.xls.v_1_2_0.GeocodedAddressType;
import net.opengis.xls.v_1_2_0.GeocodingQOSType;
import net.opengis.xls.v_1_2_0.NamedPlaceClassification;
import net.opengis.xls.v_1_2_0.NamedPlaceType;
import net.opengis.xls.v_1_2_0.ReverseGeocodeRequestType;
import net.opengis.xls.v_1_2_0.ReverseGeocodeResponseType;
import net.opengis.xls.v_1_2_0.ReverseGeocodedLocationType;
import net.opengis.xls.v_1_2_0.StreetAddressType;
import net.opengis.xls.v_1_2_0.StreetNameType;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.gofleet.openLS.handlers.GeocodingHandler;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 *
 * @author lroman
 */
@Service
public class PortalsGeocodingHandler implements GeocodingHandler {

    private static final String EPSG_4326 = "EPSG:4326";
    private static final String EPSG_23030 = "EPSG:23030";

    @Value("${geoservices.geocoding.portals.solrUrl}")
    private String SOLR_URL;
    @Value("${geoservices.geocoding.portals.solrUrlMun}")
    private String SOLR_URL_MUN;

    private static final double MAX_KM_DISTANCE_REVERSE_GEOCODING = 0.01;

    @Override
    public List<AbstractResponseParametersType> directory(DirectoryRequestType param, int maxResponses) {
        throw new UnsupportedOperationException("Directory request not supported!"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<AbstractResponseParametersType> geocoding(GeocodeRequestType param, int maxResponses) {
        List<AbstractResponseParametersType> response = new ArrayList<AbstractResponseParametersType>();

        if (param.getAddress().isEmpty()) {
            return response;
        }

        String inputAddress = param.getAddress().get(0).getFreeFormAddress();
        if (StringUtils.isBlank(inputAddress)) {
            return response;
        }
        
        

        PortalsLexer lexer =  new PortalsLexer(new ANTLRInputStream(inputAddress));
    	PortalsParser parser = new PortalsParser(new CommonTokenStream(lexer));
        parser.addErrorListener(new BaseErrorListener(){

            @Override
            public void syntaxError(Recognizer<?, ?> rcgnzr, Object o, int i, int i1, String string, RecognitionException re) {
                // We do nothing.
            }
            
        });
        
        SolrPortalQueryBuilder queryBuilder = new SolrPortalQueryBuilder();
        parser.addParseListener(queryBuilder);
        
        parser.address();        
        
        SolrQuery solrQuery = queryBuilder.getResultQuery(maxResponses);
        
        
        QueryResponse solrResult;
        try {
            solrResult = this.getSolrServer(this.SOLR_URL).query(solrQuery);
        } catch (SolrServerException ex) {
            Logger.getLogger(PortalsGeocodingHandler.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }

        GeocodeResponseType grt = new GeocodeResponseType();
        response.add(grt);

        List<GeocodeResponseListType> resultList = grt.getGeocodeResponseList();
        grt.setGeocodeResponseList(resultList);

        GeocodeResponseListType result = new GeocodeResponseListType();
        resultList.add(result);

        List<GeocodedAddressType> addresses = result.getGeocodedAddress();

        SolrDocumentList solrDocsInfo = solrResult.getResults();

        for (SolrDocument doc : solrDocsInfo) {

            GeocodedAddressType address = new GeocodedAddressType();

            address.setAddress(getDocAddress(doc));
            address.setPoint(getDocPoint(doc, EPSG_23030));

            GeocodingQOSType geocodingQOSType = new GeocodingQOSType();
            geocodingQOSType.setAccuracy(getDocDistance(doc).getValue().floatValue());
            address.setGeocodeMatchCode(geocodingQOSType);

            addresses.add(address);

        }

        result.setNumberOfGeocodedAddresses(BigInteger.valueOf(addresses.size()));

        return response;
    }

    @Override
    public List<AbstractResponseParametersType> reverseGeocode(ReverseGeocodeRequestType param, int maxResponses) {
        SolrQuery solrQuery = new SolrQuery();
        SolrQuery solrQueryMun = new SolrQuery();

        // TODO: We need to transform the point to LatLong before requesting them.
        String inputSrsName = param.getPosition().getPoint().getSrsName();
        List<Double> coordinates = param.getPosition().getPoint().getPos().getValue();

        Point point = transformPoint(coordinates.get(0), coordinates.get(1), inputSrsName, EPSG_4326);

        String posStr;
        if(inputSrsName.equals(EPSG_4326)){
            posStr = point.getY() + ", " + point.getX();
        } else {
            posStr = point.getX() + ", " + point.getY();
        }

        solrQuery.set("q", "{!func}geodist()");
        solrQuery.set("fq", String.format(Locale.ENGLISH, "{!geofilt  d=%f}", MAX_KM_DISTANCE_REVERSE_GEOCODING));
        solrQuery.set("sfield", "location");
        solrQuery.set("pt", posStr);
        solrQuery.set("sort", "score asc");
        
        if(maxResponses<=0) {
            solrQuery.setRows(10);
        } else {
            solrQuery.setRows(maxResponses);
        }

        QueryResponse solrResult;
        try {
            solrResult = this.getSolrServer(this.SOLR_URL).query(solrQuery);
        } catch (SolrServerException ex) {
            Logger.getLogger(PortalsGeocodingHandler.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }

        List<AbstractResponseParametersType> response = new ArrayList<AbstractResponseParametersType>();

        ReverseGeocodeResponseType grt = new ReverseGeocodeResponseType();
        List<ReverseGeocodedLocationType> resultList = grt.getReverseGeocodedLocation();
        response.add(grt);

        SolrDocumentList solrDocsInfo = solrResult.getResults();
        int numResults = solrDocsInfo.size();
        if(numResults > 0){
        	for (SolrDocument doc : solrDocsInfo) {
                ReverseGeocodedLocationType result = new ReverseGeocodedLocationType();

                result.setSearchCentreDistance(getDocDistance(doc));
                result.setPoint(getDocPoint(doc, inputSrsName));
                result.setAddress(getDocAddress(doc));
                resultList.add(result);
            }
        }else{
        	solrQueryMun.set("q", "*:*");
        	solrQueryMun.set("wt", "json");
        	solrQueryMun.set("fq", String.format(Locale.ENGLISH, "geom:\"Intersects(%f %f)\"", point.getX(), point.getY()));
            solrQueryMun.set("sort", "score asc");
            QueryResponse solrResultMun;
            try {
                solrResultMun = this.getSolrServer(this.SOLR_URL_MUN).query(solrQueryMun);
            } catch (SolrServerException ex) {
                Logger.getLogger(PortalsGeocodingHandler.class.getName()).log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
            SolrDocumentList solrDocsInfoMun = solrResultMun.getResults();
            for (SolrDocument doc : solrDocsInfoMun) {
                ReverseGeocodedLocationType result = new ReverseGeocodedLocationType();
                
                result.setSearchCentreDistance(getDocDistanceMun(doc, point));
                result.setPoint(getDocPointMun(point, doc, inputSrsName));
                result.setAddress(getDocAdressMun(doc));
                resultList.add(result);
            }
        }

        return response;
    }

    private SolrServer solrServer;

    private SolrServer getSolrServer(String solr_url) {
        this.solrServer = new HttpSolrServer(solr_url);
        return this.solrServer;

    }

    private AddressType getDocAddress(SolrDocument doc) {
        AddressType address = new AddressType();
        address.setCountryCode("ES");
        address.setLanguage("ES");

        StreetAddressType streetAddress = new StreetAddressType();
        address.setStreetAddress(streetAddress);

        BuildingLocatorType building = new BuildingLocatorType();
        building.setNumber(doc.get("portal").toString());

        streetAddress.setStreetLocation(new JAXBElement<BuildingLocatorType>(new QName("xls:Building"), BuildingLocatorType.class, building));

        StreetNameType streetName = new StreetNameType();

        String streetNameStr = (String) doc.get("streetName");

        String type = (String) doc.get("typeF");
        if (!StringUtils.isBlank(type)) {
            streetNameStr = type + " " + streetNameStr;
        }

        streetName.setValue(streetNameStr);
        streetAddress.getStreet().add(streetName);

        // Place
        List<NamedPlaceType> places = new ArrayList<NamedPlaceType>();

        NamedPlaceType place = new NamedPlaceType();
        place.setType(NamedPlaceClassification.MUNICIPALITY);
        place.setValue((String) doc.get("localityF"));
        places.add(place);

        address.setPlace(places);

        return address;
    }
    
    private AddressType getDocAdressMun(SolrDocument doc) {
    	AddressType address = new AddressType();
        address.setCountryCode("ES");
        address.setLanguage("ES");
        List<NamedPlaceType> places = new ArrayList<NamedPlaceType>();
        NamedPlaceType place = new NamedPlaceType();
        place.setType(NamedPlaceClassification.MUNICIPALITY);
        place.setValue((String) doc.get("name"));
        places.add(place);
        address.setPlace(places);
        return address;
    }

    private PointType getDocPoint(SolrDocument doc, String srsName) {
        // We build the result point
        PointType p = new PointType();

        p.setSrsName(srsName);

        // TODO: Transform the result from lat/lng to the received srs.
        DirectPositionType pos = new DirectPositionType();
        List<Double> resultCoordinates = new ArrayList<Double>();

        String[] location = ((String) doc.get("location")).split(",");

        Point transformPoint = transformPoint(Double.parseDouble(location[0]), Double.parseDouble(location[1]), EPSG_4326, srsName);

        if (srsName.equals(EPSG_4326)) {
            resultCoordinates.add(transformPoint.getY());
            resultCoordinates.add(transformPoint.getX());

        } else {
            resultCoordinates.add(transformPoint.getX());
            resultCoordinates.add(transformPoint.getY());
        }

        pos.setValue(resultCoordinates);

        p.setPos(pos);

        return p;
    }
    
    private PointType getDocPointMun(Point p, SolrDocument doc, String srsName){
    	PointType pointRes = new PointType();
    	pointRes.setSrsName(srsName);
    	
    	DirectPositionType pos = new DirectPositionType();
        List<Double> resultCoordinates = new ArrayList<Double>();
        
        p= transformPoint(p.getX(), p.getY(), EPSG_4326, srsName);
        
        if (srsName.equals(EPSG_4326)) {
            resultCoordinates.add(p.getY());
            resultCoordinates.add(p.getX());

        } else {
            resultCoordinates.add(p.getX());
            resultCoordinates.add(p.getY());
        }
        
        pos.setValue(resultCoordinates);
        
        pointRes.setPos(pos);
    	
    	return pointRes;
    }

    private DistanceType getDocDistance(SolrDocument doc) {
        DistanceType distance = new DistanceType();
        // Score carries the distance.
        distance.setValue(BigDecimal.valueOf((Float) doc.get("score")));
        return distance;
    }
    
    private DistanceType getDocDistanceMun(SolrDocument doc, Point p){
    	DistanceType distance = new DistanceType();
    	String str = (String)doc.get("geom");
    	WKTReader reader = new WKTReader();
    	Geometry g = null;
		try {
			g = reader.read(str);
		} catch (ParseException e) {
			e.printStackTrace();
		}
        Point centroid = g.getCentroid();
        Double dist = centroid.distance(p);
        distance.setValue(BigDecimal.valueOf(dist));
    	return distance;
    }

    private Point transformPoint(double x, double y, String inputSRS, String outputSRS) {
        Coordinate[] resultCoord = new Coordinate[1];

        resultCoord[0] = new Coordinate(x, y);
        CoordinateSequence coorS = new CoordinateArraySequence(resultCoord);
        GeometryFactory geometryFac = new GeometryFactory();
        geometryFac.createPoint(coorS);
        Geometry resultGeometry = new com.vividsolutions.jts.geom.Point(coorS, geometryFac);

        try {
            CoordinateReferenceSystem sourceCRS = CRS.decode(inputSRS);
            CoordinateReferenceSystem targetCRS = CRS.decode(outputSRS);
            MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS);
            Geometry targetGeometry = JTS.transform(resultGeometry, transform);
            return targetGeometry.getCentroid();

        } catch (MismatchedDimensionException | TransformException | FactoryException ex) {
            Logger.getLogger(PortalsGeocodingHandler.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

}
