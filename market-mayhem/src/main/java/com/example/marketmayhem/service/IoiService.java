package com.example.marketmayhem.service;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.marketmayhem.xml.IOI;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

@Service
public class IoiService {
    
    private final ConcurrentHashMap<String, IOI> ioiStore = new ConcurrentHashMap<>();
    private final JAXBContext jaxbContext;
    
    public IoiService() throws JAXBException {
        this.jaxbContext = JAXBContext.newInstance(IOI.class);
    }
    
    public IOI parseIoiFromXml(String xml) throws JAXBException {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return (IOI) unmarshaller.unmarshal(new StringReader(xml));
    }
    
    public String convertIoiToXml(IOI ioi) throws JAXBException {
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        
        StringWriter writer = new StringWriter();
        marshaller.marshal(ioi, writer);
        return writer.toString();
    }
    
    public void storeIoi(IOI ioi) {
        ioiStore.put(ioi.getIoiId(), ioi);
    }
    
    public List<IOI> getIoisBySymbol(String symbol) {
        return ioiStore.values().stream()
            .filter(ioi -> symbol.equals(ioi.getSymbol()))
            .toList();
    }
    
    public IOI createSampleIoi(String symbol) {
        return new IOI(
            UUID.randomUUID().toString(),
            symbol,
            "BUY",
            1000L,
            BigDecimal.valueOf(100.00),
            "MARKET_MAKER_1",
            "ALL"
        );
    }
}