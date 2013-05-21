package edu.one.core.sync;

import edu.one.core.datadictionary.dictionary.DefaultDictionary;
import edu.one.core.infra.Controller;
import edu.one.core.sync.aaf.AafConstantes;
import edu.one.core.sync.aaf.AafGeoffHelper;
import edu.one.core.sync.aaf.AafSaxContentHandler;
import edu.one.core.sync.aaf.WordpressHelper;
import java.io.FileReader;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public class Sync extends Controller {
	private XMLReader xr;
	private AafSaxContentHandler aafSaxHandler;
	private AafGeoffHelper aafGeoffHelper;
	private WordpressHelper wordpressHelper;

	@Override
	public void start() {
		super.start();
		aafSaxHandler = new AafSaxContentHandler(trace, new DefaultDictionary(
				vertx, container, "../edu.one.core~dataDictionary~0.1.0-SNAPSHOT/aaf-dictionary.json"));
		wordpressHelper = new WordpressHelper(trace, vertx.eventBus());
		aafGeoffHelper = new AafGeoffHelper(trace, vertx.eventBus(), wordpressHelper);
		try {
			xr = XMLReaderFactory.createXMLReader();
		} catch (SAXException ex) {
			trace.error(ex.getMessage());
		}
		xr.setContentHandler(aafSaxHandler);

		rm.get("/admin", new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest request) {
				renderView(request);
			}
		});

		rm.get("/admin/aaf/test", new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest request) {
				try {
					long startTest = System.currentTimeMillis();
					int[] crTest = syncAaf();
					long endTest = System.currentTimeMillis();

					JsonObject jo = new JsonObject().putObject("result",
						new JsonObject()
							.putString("temps", (endTest - startTest) + " ms")
							.putNumber("operations", crTest[0])
							.putNumber("rejets", crTest[1])
					);
					renderJson(request, jo);
				} catch (Exception ex) {
					trace.error(ex.toString());
					renderError(request);
				}
			}
		});
	}

	public int[] syncAaf() throws Exception {
		// Parsing of xml aaf files
		for (String filter : AafConstantes.AAF_FILTERS) {
		String [] files = vertx.fileSystem().readDirSync(
				config.getString("input-files-folder"), filter);
			for (String filePath : files) {
				xr.parse(new InputSource(new FileReader(filePath)));
			}
		}
		// Build and send geoff request
		aafGeoffHelper.sendRequest(aafSaxHandler.operations);
		// Send WP requests
		wordpressHelper.send();

		// reset objects
		int[] cr = {aafGeoffHelper.reset(),aafSaxHandler.operationsInvalides.size()};
		aafSaxHandler.reset();
		wordpressHelper.reset();
		return cr;
	}
}