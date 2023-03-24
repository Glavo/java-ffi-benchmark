import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.image.WritableImage;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@SuppressWarnings("unchecked")
public class Data extends Application {
    private static final String JNI = "JNI";
    private static final String JNA = "JNA";
    private static final String JNA_DIRECT = "JNA Direct Mapping";
    private static final String JNR = "JNR";
    private static final String JNR_IGNORE_ERROR = "JNR (Ignore Error)";
    private static final String PANAMA = "Panama";
    private static final String PANAMA_TRIVIAL = "Panama (Trivial Call)";

    private static final String THROUGHPUT_LABEL = "Throughput (ops/ms)";

    private final String CHART_STYLE = "-fx-font-family: 'MiSans SemiBold'; -fx-font-size: 16;";
    private final Font labelFont = new Font("MiSans SemiBold", 16);

    private static String getStylesheet() throws IOException {
        File f = File.createTempFile("stylesheet", ".css");
        f.deleteOnExit();
        try (var writer = Files.newBufferedWriter(f.toPath())) {
            writer.write("""
                    .root {
                        -fx-font-size: 20px;
                        -fx-font-family: MiSans;
                    }""");
        }

        return f.toPath().toUri().toString();
    }

    private BarChart<String, Number> newBarChart(String title) {
        var xAxis = new CategoryAxis();
        var yAxis = new NumberAxis();
        var chart = new BarChart<>(xAxis, yAxis);

        yAxis.setLabel(THROUGHPUT_LABEL);

        xAxis.setTickLabelFont(labelFont);
        yAxis.setTickLabelFont(labelFont);
        chart.setStyle(CHART_STYLE);

        chart.setTitle(title);
        return chart;
    }

    @Override
    public void start(Stage stage) throws Exception {
        List<String> unnamed = getParameters().getUnnamed();
        if (unnamed.isEmpty()) {
            System.err.println("Need to specify a benchmark name");
            System.exit(1);
            return;
        }

        String title = unnamed.get(0);
        int page = Integer.parseInt(getParameters().getNamed().getOrDefault("page", "0"));
        String save = getParameters().getNamed().get("save");

        Chart chart = switch (title) {
            case "NoopBenchmark" -> {
                var barChart = newBarChart(title);

                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("noop");
                series.getData().addAll(
                        new XYChart.Data<>(JNA, 19366.657),
                        new XYChart.Data<>(JNA_DIRECT, 20649.360),
                        new XYChart.Data<>(JNR, 124060.676),
                        new XYChart.Data<>(JNR_IGNORE_ERROR, 255336.652),
                        new XYChart.Data<>(JNI, 286711.963),
                        new XYChart.Data<>(PANAMA, 367987.379),
                        new XYChart.Data<>(PANAMA_TRIVIAL, 459445.651)
                );
                barChart.getData().add(series);
                yield barChart;
            }

            case "StringConvertBenchmark" -> {

                yield null;
            }
            default -> {
                System.err.println("Unknown benchmark: " + title);
                System.exit(1);
                throw new AssertionError();
            }
        };


        Scene scene = new Scene(chart, 1280, 720);
        stage.setScene(scene);
        stage.setTitle(title);
        // scene.getStylesheets().add(getStylesheet());

        if (save != null) {
            WritableImage image = new WritableImage((int) scene.getWidth(), (int) scene.getHeight());
            scene.snapshot(image);

            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(save));
            Platform.exit();
        } else {
            stage.show();
        }
    }

    public static void main(String[] args) {
        Application.launch(Data.class, args);
    }
}
