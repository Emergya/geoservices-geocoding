package com.emergya.geoservices.geocoding.portals;

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
import org.gofleet.openLS.handlers.GeocodingHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 *
 * @author lroman
 */
@Service
public class PortalsGeocodingHandler implements GeocodingHandler {

    @Value("${geoservices.geocoding.portals.solrUrl}")
    private String SOLR_URL;
    
    private static final double MAX_DISTANCE_REVERSE_GEOCODING= 0.01;

    @Override
    public List<AbstractResponseParametersType> directory(DirectoryRequestType param) {
        throw new UnsupportedOperationException("Directory request not supported!"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<AbstractResponseParametersType> geocoding(GeocodeRequestType param) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<AbstractResponseParametersType> reverseGeocode(ReverseGeocodeRequestType param) {
        SolrQuery solrQuery = new SolrQuery();

        // TODO: We need to transform the point to LatLong before requesting them.
        String inputSrsName = param.getPosition().getPoint().getSrsName();
        List<Double> coordinates = param.getPosition().getPoint().getPos().getValue();
        
        String posStr = coordinates.get(0)+", "+coordinates.get(1);

        solrQuery.set("q", "{!func}geodist()");
        solrQuery.set("fq", String.format(Locale.ENGLISH, "{!geofilt  d=%f}",MAX_DISTANCE_REVERSE_GEOCODING));
        solrQuery.set("sfield","location");
        solrQuery.set("pt", posStr);
        solrQuery.set("sort", "score asc");
        
        QueryResponse solrResult;
        try {
            solrResult = this.getSolrServer().query(solrQuery);
        } catch (SolrServerException ex) {
            Logger.getLogger(PortalsGeocodingHandler.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
        
        List<AbstractResponseParametersType> response = new ArrayList<AbstractResponseParametersType>();
        
        
        ReverseGeocodeResponseType grt = new ReverseGeocodeResponseType();
        List<ReverseGeocodedLocationType> resultList = grt.getReverseGeocodedLocation();
        response.add(grt);

        SolrDocumentList solrDocsInfo = solrResult.getResults();

        for (SolrDocument doc : solrDocsInfo) {
            ReverseGeocodedLocationType result = new ReverseGeocodedLocationType();
            
            
            
            AddressType address = new AddressType();
            result.setAddress(address);
            address.setCountryCode("ES");
            address.setLanguage("ES");
                        
            StreetAddressType streetAddress = new StreetAddressType();
            address.setStreetAddress(streetAddress);
                    
            
            BuildingLocatorType building = new BuildingLocatorType();
            building.setNumber(doc.get("portal").toString());
            
            streetAddress.setStreetLocation(new JAXBElement<BuildingLocatorType>(new QName("xls:Building"), BuildingLocatorType.class, building));
            
            
            StreetNameType streetName = new StreetNameType();   
            
            String streetNameStr = (String)doc.get("streetName");
            
            String type = (String) doc.get("typeF");
            if(!StringUtils.isBlank(type)) {
                streetNameStr = type+" "+ streetNameStr;
            }
            
            streetName.setValue(streetNameStr);
            streetAddress.getStreet().add(streetName);
            
            
            // We build the result point
            PointType p = new PointType();
            
            p.setSrsName(inputSrsName);
            
            // TODO: Transform the result from lat/lng to the received srs.
            DirectPositionType pos = new DirectPositionType();
            List<Double> resultCoordinates = new ArrayList<Double>();
            
            String[] location = ((String) doc.get("location")).split(",");
            resultCoordinates.add(Double.parseDouble(location[0]));
            resultCoordinates.add(Double.parseDouble(location[1]));
            
            pos.setValue(resultCoordinates);
            
            p.setPos(pos);
            
            result.setPoint(p);
            
            DistanceType distance = new DistanceType();
            // Score carries the distance.
            distance.setValue(BigDecimal.valueOf((Float) doc.get("score")));            
            result.setSearchCentreDistance(distance);
            
            
            // Place
            List<NamedPlaceType> places = new ArrayList<NamedPlaceType>();
            
            
            
            NamedPlaceType place = new NamedPlaceType();
            place.setType(NamedPlaceClassification.MUNICIPALITY);
            place.setValue((String)doc.get("localityF"));
            places.add(place);
            
            address.setPlace(places);
            
            resultList.add(result);
        }

        return response;
    }

    
    private SolrServer solrServer;

   

    private SolrServer getSolrServer() {
        if (this.solrServer == null) {
            this.solrServer = new HttpSolrServer(SOLR_URL);
        }

        return this.solrServer;

    }

}
