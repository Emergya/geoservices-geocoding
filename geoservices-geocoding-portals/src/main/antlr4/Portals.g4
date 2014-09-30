grammar Portals;

@header {
    package com.emergya.geoservices.geocoding.portals.antlr4;
}

address:  streetname (restAddress)?;

restAddress
    : WS portal (','? WS municipality)?
    | ',' WS municipality;

streetname 
    : QUALIFIER WS NAME
    | NAME;

portal: INT;

municipality: NAME;

QUALIFIER
    : ('calle' | 'c.' | 'cl.' | 'c\\') { setText("calle");}
    | ('avenida' | 'a.' | 'av.' | 'av\\') { setText("avenida"); }
    | ('plaza' | 'p.' | 'pl.' | 'pl\\') { setText("avenida"); };


NAME: LETTER((LETTER | ' ' )*LETTER)?;
fragment LETTER: [a-zA-Z\u0080-\u00FF];
INT: ('0'..'9')+;

WS: (' ' | '\t' |'\n')+;