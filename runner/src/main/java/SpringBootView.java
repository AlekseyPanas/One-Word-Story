import adapters.controllers.*;
import adapters.view_models.PdViewModel;
import adapters.view_models.PgeViewModel;
import adapters.view_models.SsViewModel;
import com.example.springapp.SpringApp;
import frameworks_drivers.views.View;
import org.example.ANSI;
import org.example.Log;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SpringBootView extends View {

    private static SpringBootView singeltonInstance = null;

    /**
     * Initialize singleton static instance
     */
    public static void init(CagController cagController, DcController dcController, GatController gatController,
                       GlsController glsController, GmlsController gmlsController, GscController gscController,
                       JplController jplController, LsController lsController, SsController ssController,
                       StController stController, SwController swController, UtController utController,
                            PgeViewModel pgeViewM, PdViewModel pdViewM) {
        SpringBootView.singeltonInstance = new SpringBootView(cagController, dcController, gatController, glsController, gmlsController, gscController,
                jplController, lsController, ssController, stController, swController, utController, pgeViewM, pdViewM);
    }

    /**
     * @return the static singleton instance
     */
    public static SpringBootView getInstance() {
        if(singeltonInstance == null) {
            throw new IllegalStateException("Invalid state! View is uninitialized");
        }

        return singeltonInstance;
    }

    private BufferedReader reader;
    private ConfigurableApplicationContext app;

    /**
     * SMELLY CODE
     */
    private SpringBootView(CagController cagController, DcController dcController, GatController gatController,
                           GlsController glsController, GmlsController gmlsController, GscController gscController,
                           JplController jplController, LsController lsController, SsController ssController,
                           StController stController, SwController swController, UtController utController,
                           PgeViewModel pgeViewM, PdViewModel pdViewM) {
        super(cagController, dcController, gatController, glsController, gmlsController, gscController,
                jplController, lsController, ssController, stController, swController, utController,
                pgeViewM, pdViewM);
    }

    @Override
    public void start() {
        reader = new BufferedReader(new InputStreamReader(System.in));
        app = SpringApp.startServer(this, new String[0]);
    }

    @Override
    public void run() {
        while (true) {
            try {
                String inp = reader.readLine();

                if (inp.equals("shutdown")) {
                    break;
                } else {
                    System.out.println("\"" + inp + "\" is not a valid command");
                }
            } catch (IOException e) {
                break;
            }
        }
    }

    @Override
    public void end() {
        // Calls shutdown
        Log.sendMessage("SPRING VIEW", ANSI.PURPLE, "Initiating Shutdown Use Case...");
        SsViewModel ssViewM = ssController.shutdownServer();
        Log.sendMessage("SPRING VIEW", ANSI.PURPLE, "Use Cases have been shut down!");

        app.close();
        try {
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Log.sendMessage("SPRING VIEW", ANSI.PURPLE, "Spring has closed");
    }
}
