package com.example.marketmayhem.xml;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "IOI")
@XmlAccessorType(XmlAccessType.FIELD)
public class IOI {
    
    @XmlElement(required = true)
    private String ioiId;
    
    @XmlElement(required = true)
    private String symbol;
    
    @XmlElement(required = true)
    private String side;
    
    @XmlElement(required = true)
    private Long quantity;
    
    @XmlElement
    private BigDecimal price;
    
    @XmlElement(required = true)
    private String sender;
    
    @XmlElement(required = true)
    private String target;
    
    @XmlElement
    private String validUntil;
    
    @XmlElement
    private String text;
    
    // Constructors
    public IOI() {}
    
    public IOI(String ioiId, String symbol, String side, Long quantity, 
               BigDecimal price, String sender, String target) {
        this.ioiId = ioiId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.sender = sender;
        this.target = target;
        this.validUntil = Instant.now().plusSeconds(300).toString(); // 5 minutes
    }
    
    // Getters and Setters
    public String getIoiId() { return ioiId; }
    public void setIoiId(String ioiId) { this.ioiId = ioiId; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    
    public Long getQuantity() { return quantity; }
    public void setQuantity(Long quantity) { this.quantity = quantity; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    
    public String getValidUntil() { return validUntil; }
    public void setValidUntil(String validUntil) { this.validUntil = validUntil; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}