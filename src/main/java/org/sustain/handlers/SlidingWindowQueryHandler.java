package org.sustain.handlers;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.sustain.SlidingWindowRequest;
import org.sustain.SlidingWindowResponse;
import org.sustain.db.mongodb.DBConnection;
import org.sustain.util.Constants;

import java.util.ArrayList;
import java.util.Arrays;

public class SlidingWindowQueryHandler extends GrpcHandler<SlidingWindowRequest, SlidingWindowResponse> {
    private static final Logger log = LogManager.getFormatterLogger(SlidingWindowQueryHandler.class);

    public SlidingWindowQueryHandler(SlidingWindowRequest request,
                                     StreamObserver<SlidingWindowResponse> responseObserver) {
        super(request, responseObserver);
    }

    @Override
    public void handleRequest() {
        logRequest(request);
        MongoDatabase db = DBConnection.getConnection();
        MongoCollection<Document> collection = db.getCollection(request.getCollection());

        int days = request.getDays();
        String feature = request.getFeature();
        ArrayList<String> gisJoins = new ArrayList<>(request.getGisJoinsList());
        for (String gisJoin : gisJoins) {
            AggregateIterable<Document> documents = processSingleGisJoin(gisJoin, feature, days, collection);
            SlidingWindowResponse.Builder responseBuilder = SlidingWindowResponse.newBuilder();
            responseBuilder.setGisJoin(gisJoin);
            ArrayList<String> movingAverages = new ArrayList<>();
            for (Document document : documents) {
                movingAverages.add(document.toString());
            }
            responseBuilder.addAllMovingAverages(movingAverages);
            responseObserver.onNext(responseBuilder.build());
        }

        responseObserver.onCompleted();
    }

    private AggregateIterable<Document> processSingleGisJoin(String gisJoin, String feature, int days,
                                                             MongoCollection<Document> mongoCollection) {
        log.info("Processing GISJOIN: " + gisJoin);
        // The following aggregation query is based on the raw MongoDB query found at https://pastebin.com/HUciUXZW
        AggregateIterable<Document> aggregateIterable = mongoCollection.aggregate(Arrays.asList(
                new Document("$match", new Document(Constants.GIS_JOIN, gisJoin)),
                new Document("$sort", new Document("formatted_date", 1)),
                new Document("$group", new Document("_id", "$" + Constants.GIS_JOIN)
                        .append("prx", new Document("$push",
                                new Document("v", "$" + feature)
                                        .append("date", "$formatted_date")
                        ))),
                new Document(
                        "$addFields",
                        new Document("numDays", days)
                                .append("startDate",
                                        new Document("$arrayElemAt", Arrays.asList("$prx.date", 0)))
                ),
                new Document("$addFields", new Document("prx",
                        new Document("$map",
                                new Document("input", new Document("$range",
                                        Arrays.asList(0, new Document("$subtract",
                                                Arrays.asList(new Document("$size", "$prx"), days - 1)
                                        ))))
                                        .append("as", "z")
                                        .append("in", new Document("avg",
                                                new Document("$avg",
                                                        new Document("$slice", Arrays.asList("$prx.v", "$$z", days))
                                                ))
                                                .append("date", new Document("$arrayElemAt",
                                                        Arrays.asList("$prx.date", new Document("$add",
                                                                Arrays.asList("$$z", days - 1)))
                                                ))
                                        )
                        )
                )
                )
        ));

        return aggregateIterable;
    }
}