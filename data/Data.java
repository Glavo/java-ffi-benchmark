import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

@SuppressWarnings("unchecked")
public class Data extends Application {
    private static final String JNI = "JNI";
    private static final String JNA = "JNA";
    private static final String JNA_DIRECT = "JNA Direct Mapping";
    private static final String JNR = "JNR";
    private static final String JNR_IGNORE_ERROR = "JNR (Ignore Error)";
    private static final String PANAMA = "Panama";
    private static final String PANAMA_TRIVIAL = "Panama (Trivial Call)";
    private static final String PANAMA_NO_ALLOCATE = "Panama (No Allocate)";

    private static final Color JNI_COLOR = Color.web("#A9A9A9");
    private static final Color JNA_COLOR = Color.web("#FF4D00");
    private static final Color JNA_DIRECT_COLOR = Color.web("#FFA07A");
    private static final Color JNR_COLOR = Color.web("#FFA500");
    private static final Color JNR_IGNORE_ERROR_COLOR = Color.web("#FFD700");
    private static final Color PANAMA_COLOR = Color.web("#6640FF");
    private static final Color PANAMA_TRIVIAL_COLOR = Color.web("#B399FF");
    private static final Color PANAMA_NO_ALLOCATE_COLOR = Color.web("#B8A1CF");

    private static final String THROUGHPUT_LABEL = "Throughput (ops/ms)";

    private final String CHART_STYLE = "-fx-font-family: 'MiSans SemiBold'; -fx-font-size: 16;";
    private final Font labelFont = new Font("MiSans SemiBold", 16);

    private static XYChart.Series<String, Number> series(String name, XYChart.Data<String, Number>... data) {
        return new XYChart.Series<>(name, FXCollections.observableArrayList(data));
    }

    private static XYChart.Data<String, Number> data(String name, Number value) {
        return new XYChart.Data<>(name, value);
    }

    private static XYChart.Data<String, Number> data(Number name, Number value) {
        return new XYChart.Data<>(name.toString(), value);
    }

    private <C extends XYChart<String, Number>> C newXYChart(
            String title, BiFunction<Axis<String>, Axis<Number>, C> creator
    ) {
        var xAxis = new CategoryAxis();
        var yAxis = new NumberAxis();
        var chart = creator.apply(xAxis, yAxis);

        yAxis.setLabel(THROUGHPUT_LABEL);

        xAxis.setTickLabelFont(labelFont);
        yAxis.setTickLabelFont(labelFont);
        chart.setStyle(CHART_STYLE);

        chart.setTitle(title);
        return chart;
    }

    private static Color getColor(String name) {
        return switch (name) {
            case JNI -> JNI_COLOR;
            case JNA -> JNA_COLOR;
            case JNA_DIRECT -> JNA_DIRECT_COLOR;
            case JNR -> JNR_COLOR;
            case JNR_IGNORE_ERROR -> JNR_IGNORE_ERROR_COLOR;
            case PANAMA -> PANAMA_COLOR;
            case PANAMA_TRIVIAL -> PANAMA_TRIVIAL_COLOR;
            case PANAMA_NO_ALLOCATE -> PANAMA_NO_ALLOCATE_COLOR;
            default -> null;
        };
    }

    private static String toHexColor(Color color) {
        int r = (int) Math.round(color.getRed() * 255.0);
        int g = (int) Math.round(color.getGreen() * 255.0);
        int b = (int) Math.round(color.getBlue() * 255.0);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    @Override
    public void start(Stage stage) throws Exception {
        List<String> unnamed = getParameters().getUnnamed();
        if (unnamed.isEmpty()) {
            System.err.println("Need to specify a benchmark name");
            System.exit(1);
            return;
        }

        var width = 1280;
        var height = 720;

        String title = unnamed.get(0);
        String method = getParameters().getNamed().get("method");
        String fileName = getParameters().getNamed().get("save");
        int page = Integer.parseInt(getParameters().getNamed().getOrDefault("page", "0"));

        XYChart<String, Number> chart = switch (title) {
            case "NoopBenchmark" -> {
                var barChart = newXYChart(title, BarChart::new);
                barChart.getData().add(series("noop",
                        data(JNA, 19372.933),
                        data(JNA_DIRECT, 20595.690),
                        data(JNR, 124115.069),
                        data(JNR_IGNORE_ERROR, 241616.003),
                        data(JNI, 287143.357),
                        data(PANAMA, 325322.988),
                        data(PANAMA_TRIVIAL, 459682.023)
                ));
                yield barChart;
            }
            case "SysinfoBenchmark" -> {
                var barChart = newXYChart(title, BarChart::new);
                barChart.setTitle("SysinfoBenchmark");

                if (page == 0) {
                    barChart.getData().add(series("getMemUnit",
                            data(JNA, 153.982),
                            data(JNA_DIRECT, 153.339),
                            data(JNR, 3500.917),
                            data(JNI, 7489.950),
                            data(PANAMA, 5469.507),
                            data(PANAMA_TRIVIAL, 5353.859)
                    ));
                } else if (page == 1) {
                    width = 640;

                    barChart.getData().add(series("getMemUnit",
                            data(JNI, 7489.950),
                            data(PANAMA, 5469.507),
                            data(PANAMA_NO_ALLOCATE, 7077.384)
                    ));
                }
                yield barChart;
            }
            case "StringConvertBenchmark" -> {
                var lineChart = newXYChart(title, LineChart::new);
                lineChart.getXAxis().setLabel("String Length");

                XYChart.Series<String, Number> jna, jnaDirect, jnr, jni, panama, panamaTrivial;

                if (method.equals("getStringFromNative")) {
                    lineChart.setTitle("StringConvertBenchmark (C to Java)");

                    jna = series(JNA,
                            data(0, 4092.150),
                            data(16, 3784.092),
                            data(64, 3853.505),
                            data(256, 3663.010),
                            data(1024, 2712.936),
                            data(4096, 1175.953)
                    );

                    jnaDirect = series(JNA_DIRECT,
                            data(0, 2693.722),
                            data(16, 2643.298),
                            data(64, 2553.824),
                            data(256, 2439.881),
                            data(1024, 2003.651),
                            data(4096, 1054.896)
                    );


                    jnr = series(JNR,
                            data(0, 27917.487),
                            data(16, 16350.021),
                            data(64, 16123.189),
                            data(256, 11037.584),
                            data(1024, 3150.209),
                            data(4096, 849.189)
                    );

                    jni = series(JNI,
                            data(0, 23394.797),
                            data(16, 17889.325),
                            data(64, 12338.911),
                            data(256, 6004.696),
                            data(1024, 1942.420),
                            data(4096, 481.794)
                    );

                    panama = series(PANAMA,
                            data(0, 145726.134),
                            data(16, 39877.469),
                            data(64, 15771.128),
                            data(256, 5164.480),
                            data(1024, 1251.357),
                            data(4096, 308.003)
                    );

                    panamaTrivial = series(PANAMA_TRIVIAL,
                            data(0, 177633.562),
                            data(16, 41196.992),
                            data(64, 15936.457),
                            data(256, 5198.498),
                            data(1024, 1163.393),
                            data(4096, 309.323)
                    );

                    lineChart.getData().setAll(jna, jnaDirect, jnr, jni, panama, panamaTrivial);

                    if (page == 1) {
                        height = 1260;

                        lineChart.getData().removeIf(it -> it.getName().equals(JNA_DIRECT) || it.getName().equals(PANAMA_TRIVIAL));
                        for (XYChart.Series<String, Number> series : lineChart.getData()) {
                            series.getData().remove(0, 2);
                        }

                        // reset color
                        var copy = List.copyOf(lineChart.getData());
                        lineChart.getData().clear();
                        lineChart.getData().setAll(copy);
                    }

                } else if (method.equals("passStringToNative")) {
                    lineChart.setTitle("StringConvertBenchmark (Java to C)");

                    jna = series(JNA,
                            data(0, 1375.619),
                            data(16, 1310.013),
                            data(64, 1264.112),
                            data(256, 951.284),
                            data(1024, 865.153),
                            data(4096, 531.490)
                    );

                    jnaDirect = series(JNA_DIRECT,
                            data(0, 1791.365),
                            data(16, 1774.855),
                            data(64, 1740.003),
                            data(256, 1716.909),
                            data(1024, 1651.610),
                            data(4096, 1110.712)
                    );

                    jnr = series(JNR,
                            data(0, 19713.083),
                            data(16, 9985.897),
                            data(64, 5514.056),
                            data(256, 1926.590),
                            data(1024, 783.466),
                            data(4096, 197.084)
                    );

                    panama = series(PANAMA,
                            data(0, 19152.701),
                            data(16, /*16159.395*/ 18314.284), // Abnormal data, so I used another result
                            data(64, 17919.539),
                            data(256, 15487.281),
                            data(1024, 8463.958),
                            data(4096, 2342.041)
                    );

                    panamaTrivial = series(PANAMA_TRIVIAL,
                            data(0, 19397.366),
                            data(16, 18960.731),
                            data(64, 18422.506),
                            data(256, 16087.884),
                            data(1024, 8901.188),
                            data(4096, 2315.055)
                    );

                    lineChart.getData().setAll(jna, jnaDirect, jnr, panama, panamaTrivial);

                } else {
                    System.err.println("Unknown method: " + method);
                    System.exit(1);
                    throw new AssertionError();
                }
                yield lineChart;
            }
            case "QSortBenchmark" -> {
                var lineChart = newXYChart(title, LineChart::new);
                lineChart.getXAxis().setLabel("Element Counts");
                lineChart.getData().setAll(
                        series(JNA,
                                data(8, 136.657),
                                data(16, 50.465),
                                data(32, 21.446),
                                data(64, 9.102),
                                data(128, 3.897)
                        ),
                        series(JNA_DIRECT,
                                data(8, 189.484),
                                data(16, 74.036),
                                data(32, 30.134),
                                data(64, 12.798),
                                data(128, 5.432)
                        ),
                        series(JNR,
                                data(8, 678.276),
                                data(16, 259.743),
                                data(32, 113.670),
                                data(64, 47.149),
                                data(128, 20.324)
                        ),
                        series(JNI,
                                data(8, 1136.358),
                                data(16, 440.918),
                                data(32, 176.537),
                                data(64, 73.781),
                                data(128, 31.002)
                        ),
                        series(PANAMA,
                                data(8, 4086.198),
                                data(16, 1507.481),
                                data(32, 646.251),
                                data(64, 280.448),
                                data(128, 120.636)
                        )
                );
                yield lineChart;
            }
            default -> {
                System.err.println("Unknown benchmark: " + title);
                System.exit(1);
                throw new AssertionError();
            }
        };

        List<Color> colors = new ArrayList<>();
        if (chart instanceof LineChart<String, Number>){
            for (XYChart.Series<?, ?> series : chart.getData()) {
                colors.add(getColor(series.getName()));
            }
        }

        Scene scene = new Scene(chart, width, height);
        stage.setScene(scene);
        stage.setTitle(title);

        File tempFile = File.createTempFile("chart-", ".css");
        tempFile.deleteOnExit();
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile.toPath())) {
            int i = 0;
            for (Color color : colors) {
                if (color != null) {
                    writer.write("""
                                .default-color%1$s.chart-series-line {
                                    -fx-stroke: %2$s;
                                }
                                
                                .default-color%1$s.chart-line-symbol {
                                    -fx-background-color: %2$s;
                                }
                                
                                """.formatted(i, toHexColor(color)));
                }
                i++;
            }
        }
        scene.getStylesheets().add(tempFile.toURI().toString());

        if (fileName != null) {
            WritableImage image = new WritableImage((int) scene.getWidth(), (int) scene.getHeight());
            scene.snapshot(image);

            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(fileName));
            Platform.exit();
        } else {
            stage.show();
        }
    }

    public static void main(String[] args) {
        Application.launch(Data.class, args);
    }
}
