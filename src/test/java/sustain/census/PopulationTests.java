package sustain.census;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sustain.census.CensusGrpc;
import org.sustain.census.CensusServer;
import org.sustain.census.ClientHelper;
import org.sustain.census.Constants;
import org.sustain.census.Decade;
import org.sustain.census.TotalPopulationResponse;
import org.sustain.census.db.Util;

import static org.sustain.census.Constants.CensusResolutions.COUNTY;
import static org.sustain.census.Constants.CensusResolutions.STATE;
import static org.sustain.census.Constants.CensusResolutions.TRACT;
import static sustain.census.TestUtil.decades;

public class PopulationTests {
    private static final Logger log = LogManager.getLogger(PopulationTests.class);
    private static CensusGrpc.CensusBlockingStub censusBlockingStub;
    private static ManagedChannel channel;
    private static CensusServer server;
    private static ClientHelper clientHelper;

    @BeforeAll
    static void init() throws InterruptedException {
        server = new CensusServer();
        new ServerRunner(server).start();
        Thread.sleep(2000);
        String target = Util.getProperty(Constants.Server.HOST) + ":" + Constants.Server.PORT;
        channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        censusBlockingStub = CensusGrpc.newBlockingStub(channel);
        clientHelper = new ClientHelper(censusBlockingStub);
    }

    @Test
    void testStatePopulation() {
        for (Decade decade : decades) {
            TotalPopulationResponse populationResponse = clientHelper.requestTotalPopulation(STATE, 24.5, -82, decade);
            Assertions.assertNotNull(populationResponse);
            Assertions.assertTrue(populationResponse.getPopulation() >= 0);
        }
    }

    @Test
    void testCountyPopulation() {
        for (Decade decade : decades) {
            TotalPopulationResponse populationResponse = clientHelper.requestTotalPopulation(COUNTY, 24.5, -82, decade);
            Assertions.assertNotNull(populationResponse);
            Assertions.assertTrue(populationResponse.getPopulation() >= 0);
        }
    }

    @Test
    void testTractPopulation() {
        for (Decade decade : decades) {
            TotalPopulationResponse populationResponse = clientHelper.requestTotalPopulation(TRACT, 24.5, -82, decade);
            Assertions.assertNotNull(populationResponse);
            Assertions.assertTrue(populationResponse.getPopulation() >= 0);
        }
    }

    @AfterAll
    static void cleanUp() {
        server.shutdownNow();
    }
}
