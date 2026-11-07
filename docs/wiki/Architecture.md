# Architecture

```
UI (Compose) -> ViewModel -> data services -> drivers / transports / Room / DataStore
```

* `domain/` holds pure Kotlin: models, the template engine, label planner, barcodes, dithering, CSV and .xlsx parsing, and the driver and transport interfaces.
* `data/` holds the ESC/POS and label drivers, Bluetooth and USB transports, discovery, Android rendering and the Room database.
* `ui/` has one package per screen: home, printers, templates, labels, paper, pos, quick, file, scan, history, settings.

Preview and print share code. The editors show the blocks that `TemplateEngine.resolve` produces, which are the same ones the compilers send to the printer. The label preview is drawn by the same `LabelRaster` that rasterises label text for printing.

## Add a printer driver

1. Add a value to `PrinterLanguage`.
2. Implement `PrinterDriver`. For a label language, extend `LabelDriver` and implement `emit`.
3. Register it in `DriverRegistry`.
4. Add a unit test that asserts the emitted bytes. See `LabelTest`.

A new connection type, such as Wi-Fi on port 9100, means implementing `PrinterTransport` and adding it to `ConnectionType` and `TransportFactory`.
