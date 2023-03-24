import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.image.WritableImage;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
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

        Chart chart = switch (title) {
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
                            data("Panama (No Allocate)", 7077.384)
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
                                data(16, 54.354),
                                data(32, 21.476),
                                data(64, 9.371)
                        ),
                        series(JNA_DIRECT,
                                data(16, 74.897),
                                data(32, 30.507),
                                data(64, 12.634)
                        ),
                        series(JNR,
                                data(16, 267.515),
                                data(32, 103.865),
                                data(64, 44.477)
                        ),
                        series(JNI,
                                data(16, 428.445),
                                data(32, 174.683),
                                data(64, 71.399)
                        ),
                        series(PANAMA,
                                data(16, 1607.100),
                                data(32, 659.764),
                                data(64, 266.252)
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

        Scene scene = new Scene(chart, width, height);
        stage.setScene(scene);
        stage.setTitle(title);

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
