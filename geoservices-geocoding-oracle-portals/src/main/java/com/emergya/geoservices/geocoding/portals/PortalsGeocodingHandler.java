package com.emergya.geoservices.geocoding.portals;

import java.util.List;
import net.opengis.xls.v_1_2_0.AbstractResponseParametersType;
import net.opengis.xls.v_1_2_0.DirectoryRequestType;
import net.opengis.xls.v_1_2_0.GeocodeRequestType;
import net.opengis.xls.v_1_2_0.ReverseGeocodeRequestType;
import org.gofleet.openLS.handlers.GeocodingHandler;
import org.springframework.stereotype.Service;

/**
 *
 * @author lroman
 */
@Service
public class PortalsGeocodingHandler implements GeocodingHandler {

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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
