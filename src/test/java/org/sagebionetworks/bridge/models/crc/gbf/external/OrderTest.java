package org.sagebionetworks.bridge.models.crc.gbf.external;

import static org.sagebionetworks.bridge.services.GBFOrderService.XML_MAPPER;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

public class OrderTest {
    
    @Test
    public void serialize() throws JsonProcessingException {
        Order o = new Order(true, "orderNumber", "clientAccount", LocalDate.now(),
                new Order.ShippingInfo(new Order.ShippingInfo.Address(
                        "Patient Name", "123 Abc St", "Apt 456", "New York",
                        "NY", "10001", "United States", "(858) 111-1234")
                        , "Fedex 2Day AM"), new Order.LineItem("FM-00049", 1));
        XML_MAPPER.writeValueAsString(o);

        List<Order> orderList = Lists.newArrayList(o, o);
        XML_MAPPER.writeValueAsString(orderList);

        Order.Orders orders = new Order.Orders(Lists.newArrayList(o, o));
        XML_MAPPER.writeValueAsString(orders);
    }

    @Test
    public void deserializeShipConf() throws JsonProcessingException {
        String s = """
                <ShippingConfirmation OrderNumber="ID-000802" Shipper="791113" ShipVia="FedEx Ground" ShipDate="3/23/2016" ClientID="A10056001">
                		<Tracking>016050765404963</Tracking>
                		<Item ItemNumber="1 Pack Kit" LotNumber="J3AR6-10/31/2016" ExpireDate="2016-10-31" ShippedQty="1">
                			<TubeSerial>T123456789</TubeSerial>
                			<ReturnTracking>123456789012</ReturnTracking>
                		</Item>
                		<Item ItemNumber="1 Pack Kit" LotNumber="J3AR6-10/31/2016" ExpireDate="2016-10-31" ShippedQty="1">
                			<TubeSerial>T123456790</TubeSerial>
                			<ReturnTracking>123456789023</ReturnTracking>
                		</Item>\s
                	</ShippingConfirmation>\
                """;
        ShippingConfirmation shippingConfirmation = XML_MAPPER.readValue(s, ShippingConfirmation.class);
        XML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(shippingConfirmation);
    }

    @Test
    public void deserializeShipConfs() throws JsonProcessingException {
        String s = """
                <?xml version="1.0" encoding="utf-8"?>
                <ShippingConfirmations>
                	<ShippingConfirmation OrderNumber="ID-000802" Shipper="791113" ShipVia="FedEx Ground" ShipDate="3/23/2016" ClientID="A10056001">
                		<Tracking>016050765404963</Tracking>
                		<Item ItemNumber="1 Pack Kit" LotNumber="J3AR6-10/31/2016" ExpireDate="2016-10-31" ShippedQty="1">
                			<TubeSerial>T123456789</TubeSerial>
                			<ReturnTracking>123456789012</ReturnTracking>
                		</Item>
                		<Item ItemNumber="1 Pack Kit" LotNumber="J3AR6-10/31/2016" ExpireDate="2016-10-31" ShippedQty="1">
                			<TubeSerial>T123456790</TubeSerial>
                			<ReturnTracking>123456789023</ReturnTracking>
                		</Item>\s
                	</ShippingConfirmation>
                	<ShippingConfirmation OrderNumber="ID-000804" Shipper="791203" ShipVia="FedEx Overnight" ShipDate="3/23/2016" ClientID="A10000018">
                		<Tracking>016050456760505</Tracking>
                		<Tracking>016050456504567</Tracking>
                		<Item ItemNumber="5 Pack Kit" LotNumber="J3A63-09/30/2016" ExpireDate="2016-09-30" ShippedQty="1">
                			<TubeSerial>T123456791</TubeSerial>
                			<TubeSerial>T123456792</TubeSerial>
                			<TubeSerial>T123456793</TubeSerial>
                			<TubeSerial>T123456794</TubeSerial>
                			<TubeSerial>T123456795</TubeSerial>
                			<ReturnTracking>123456789034</ReturnTracking>
                			<ReturnTracking>123456789045</ReturnTracking>
                			<ReturnTracking>123456789056</ReturnTracking>
                			<ReturnTracking>123456789067</ReturnTracking>
                			<ReturnTracking>123456789078</ReturnTracking>
                		</Item>
                		<Item ItemNumber="5 Pack Kit" LotNumber="J3A63-09/30/2016" ExpireDate="2016-09-30" ShippedQty="1">
                			<TubeSerial>T123456796</TubeSerial>
                			<TubeSerial>T123456797</TubeSerial>
                			<TubeSerial>T123456798</TubeSerial>
                			<TubeSerial>T123456799</TubeSerial>
                			<TubeSerial>T123456800</TubeSerial>
                			<ReturnTracking>123456789089</ReturnTracking>
                			<ReturnTracking>123456789090</ReturnTracking>
                			<ReturnTracking>123456789101</ReturnTracking>
                			<ReturnTracking>123456789112</ReturnTracking>
                			<ReturnTracking>123456789123</ReturnTracking>
                		</Item>
                	</ShippingConfirmation>
                </ShippingConfirmations>
                """;
        
        ShippingConfirmations shippingConfirmation = XML_MAPPER.readValue(s, ShippingConfirmations.class);
        XML_MAPPER.readTree(s);
    
        XML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(shippingConfirmation);
        new ObjectMapper().writeValueAsString(shippingConfirmation);
    }

    @Test
    public void deserializeError() throws JsonProcessingException {
        String s = "<Response>\r\n  <Error>Failed to parse date range</Error>\r\n</Response>";
    
        JsonNode node = XML_MAPPER.readTree(s);
    
        node.get("Error").asText();
    }
}