/**
 * $Id: WSDLParser.java,v 1.9 2005-08-04 02:34:03 kwalsh Exp $
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.sun.xml.ws.wsdl.parser;

import com.sun.xml.ws.streaming.XMLReader;
import com.sun.xml.ws.streaming.XMLReaderException;
import com.sun.xml.ws.streaming.XMLReaderFactory;
import com.sun.xml.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.ws.wsdl.WSDLContext;
import org.xml.sax.InputSource;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.soap.SOAPBinding;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static com.sun.xml.ws.streaming.XMLReader.START;
import static com.sun.xml.ws.streaming.XMLReader.END;
import static com.sun.xml.ws.streaming.XMLReader.EOF;


/**
 * @author WS Development Team
 */
public class WSDLParser {
    //private String serviceName;
    private String tns;
    //private String location;
    private String systemId;
   // private String bindingId;
    private WSDLContext wsdlContext;

    //todo:rename
    private LocalizableMessageFactory messageFactory =
        new LocalizableMessageFactory("com.sun.xml.ws.resources.wsdl");

    private static XMLReader reader;

    //used for debugging for the most part
    //do not remove
    private boolean isImport;
    private boolean importsDone;
    private boolean bindingDone;
    private boolean locationDone;
    private boolean portDone;
    private boolean serviceDone;

    public WSDLParser() {
    }

    public WSDLContext getWSDLContext() {
        return wsdlContext;
    }

    public WSDLContext parse(InputStream is, WSDLContext wsdlcontext)
        throws Exception {

        InputSource source = new InputSource(is);
        systemId = source.getSystemId();

        try {
            reader =
                XMLReaderFactory.newInstance().createXMLReader(is);
            return parseWSDL(reader, wsdlcontext);
        } catch (XMLReaderException e) {
            throw new Exception("wsdl.xmlReader", e);
        }
    }

    public WSDLContext parseWSDL(XMLReader reader, WSDLContext wsdlcontext) {

        int state = reader.next();
        do {
            switch (reader.getState()) {
                case START:
                    QName name = reader.getName();
                    if (WSDLConstants.QNAME_DEFINITIONS.equals(name)) {
                        tns = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_TNS);
                        if (wsdlcontext.getTargetNamespace() == null)
                            wsdlcontext.setTargetNamespace(tns);
                        reader.nextElementContent();
                    } else if (WSDLConstants.QNAME_IMPORT.equals(name)) {
                        parseWSDLImport(reader, wsdlcontext);
                        reader.next();
                        reader.nextElementContent();
                    } else if (WSDLConstants.QNAME_DOCUMENTATION.equals(name)) {
                        reader.skipElement();
                        reader.nextElementContent();
                    } else if (WSDLConstants.QNAME_BINDING.equals(name)) {
                        parseWSDLBinding(reader, wsdlcontext);
                    } else if (WSDLConstants.QNAME_SERVICE.equals(name)) {
                        parseWSDLService(reader, wsdlcontext);
                    } else {
                        reader.skipElement();
                        reader.nextElementContent();
                    }
                    break;
                case END:
                    reader.nextElementContent();
                    break;
            }
            if (isDone()) //|| reader.getName().equals(WSDLConstants.QNAME_DEFINITIONS))
                break;
        } while (reader.getState() != EOF);

        while (reader.getState() != XMLReader.EOF)
            reader.next();

        reader.close();

        return (wsdlContext = wsdlcontext);
    }


    protected WSDLContext parseWSDLImport(XMLReader reader, WSDLContext wsdlcontext) {
        if (WSDLConstants.QNAME_IMPORT.equals(reader.getName())) {
            isImport = true;
            URL importURL = null;
            String importLocation =
                ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_LOCATION);
            URI temp = null;
            try {
                temp = new URI(importLocation);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            if (temp.isAbsolute()) {
                try {
                    importURL = temp.toURL();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            } else {

                String origWsdlPath = wsdlcontext.getOrigURLPath();
                int index = origWsdlPath.lastIndexOf("/");
                String base = origWsdlPath.substring(0, index);

                URI owsdlLoc = null;
                String host = null;
                int port = 0;

                try {
                    owsdlLoc = new URI(wsdlcontext.getWsdlLocation().toExternalForm());
                    host = owsdlLoc.getHost();
                    port = owsdlLoc.getPort();
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }

                if (owsdlLoc != null) {
                    File file = null;
                    if (owsdlLoc.getScheme().equals("file")) {
                        String path = owsdlLoc.getPath();
                        if (path != null) {
                            file = new File(path);
                            File parent = file.getParentFile();
                            if (parent.isDirectory()) {
                                try {
                                    String absPath = parent.getCanonicalPath();
                                    String importString = "file:/" +
                                        absPath + "/" + importLocation;
                                    importURL = new URL(importString);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        //todo://needs to be update for http imports
                    } else if (owsdlLoc.getScheme().equals("http"))
                        try {
                            importURL = new URL("http://" + host + ":" + port + base + "/" + importLocation);
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                }

                try {
                    parse(importURL.openStream(), wsdlcontext);
                    isImport = false;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    protected void parseWSDLBinding(XMLReader reader, WSDLContext wsdlcontext) {

        if (WSDLConstants.QNAME_BINDING.equals(reader.getName())) {
            reader.nextElementContent();
            do {
                switch (reader.getState()) {
                    case START:
                        QName name = reader.getName();
                        if (WSDLConstants.NS_SOAP_BINDING.equals(reader.getName())) {
                            wsdlcontext.setBindingID(SOAPBinding.SOAP11HTTP_BINDING);
                            reader.next();
                            reader.nextElementContent();
                        } else if (WSDLConstants.NS_SOAP12_BINDING.equals(name)) {
                            wsdlcontext.setBindingID(SOAPBinding.SOAP12HTTP_BINDING);
                            reader.next();
                            reader.nextElementContent();
                        } else if (WSDLConstants.QNAME_DOCUMENTATION.equals(name)) {
                            reader.skipElement();
                            reader.nextElementContent();
                        } else if (WSDLConstants.QNAME_OPERATION.equals(name)) {
                            reader.skipElement();
                            reader.nextElementContent();
                        } else {
                            reader.skipElement();
                            reader.nextElementContent();
                        }
                }
                if (reader.getName().equals(WSDLConstants.QNAME_BINDING))
                    break;
            } while (reader.getState() != EOF);

            reader.next();
            reader.nextElementContent();
        }
    }

    protected void parseWSDLService(XMLReader reader, WSDLContext wsdlcontext) {

        if (WSDLConstants.QNAME_SERVICE.equals(reader.getName())) {
            String serviceName =ParserUtil.getMandatoryAttribute(reader, WSDLConstants.ATTR_NAME);
            reader.nextElementContent();
            LinkedHashMap<QName, String> portsMap = new LinkedHashMap<QName, String>();
            do {
                switch (reader.getState()) {
                    case START:
                        if (WSDLConstants.QNAME_PORT.equals(reader.getName())) {
                            parseWSDLPort(reader, wsdlcontext, portsMap);
                        } else {
                            reader.skipElement();
                            reader.nextElementContent();
                        }
                        break;
                    case END:
                        reader.nextElementContent();
                }
                //if (reader.getName().equals(WSDLConstants.QNAME_SERVICE))
                //     break;
            } while (reader.getState() != EOF);
            wsdlcontext.addService2Ports(new QName(wsdlcontext.getTargetNamespace(), serviceName), portsMap);
            serviceDone = true;
            reader.next();
            reader.nextElementContent();
            //if (reader.getName().equals(WSDLConstants.QNAME_DEFINITIONS))
            //    return;
        }
    }

    protected void parseWSDLPort(XMLReader reader, WSDLContext wsdlcontext, HashMap portsMap) {

        if (WSDLConstants.QNAME_PORT.equals(reader.getName())) {
            String portName = ParserUtil.getMandatoryAttribute(reader, WSDLConstants.ATTR_NAME);
            reader.nextElementContent();

            do {
                switch (reader.getState()) {
                    case START:
                        if (WSDLConstants.NS_SOAP_BINDING_ADDRESS.equals(reader.getName()) ||
                            WSDLConstants.NS_SOAP12_BINDING_ADDRESS.equals(reader.getName())) {
                            String endpoint = ParserUtil.getMandatoryAttribute(reader, WSDLConstants.ATTR_LOCATION);
                            portsMap.put(new QName(wsdlcontext.getTargetNamespace(), portName), endpoint);
                            locationDone = true;
                            reader.next();
                            reader.nextElementContent();
                        } else {
                            reader.skipElement();
                            reader.nextElementContent();
                        }
                        break;
                    case END:
                        reader.nextElementContent();
                }
                if (reader.getName().equals(WSDLConstants.QNAME_PORT)) {
                    break;
                }

            } while (reader.getState() != EOF);
            portDone = true;
            reader.next();
            reader.nextElementContent();
        }
    }

    private boolean isDone() {
        return (bindingDone() && locationDone());
    }

    private boolean isImport() {
        return isImport;
    }

    private void setIsImport(boolean imp) {
        isImport = imp;
    }

    private boolean importDone() {
        return importsDone;
    }

    private void setImportsDone(boolean imp) {
        importsDone = imp;
    }

    private boolean bindingDone() {
        return bindingDone;
    }

    private boolean locationDone() {
        return locationDone;
    }




}