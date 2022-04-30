package luhmann;

import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.DocinfoProcessor;
import org.asciidoctor.extension.Location;
import org.asciidoctor.extension.LocationType;

@Location(LocationType.FOOTER)
public class RefreshDocinfoProcessor extends DocinfoProcessor {

    @Override
    public String process(Document document) {
        return "<script>"
            + "var socket = new WebSocket(location.origin.replace(/^http/, 'ws') + '/ws');"
            + "socket.addEventListener('message', function (event) { window.location.reload(); });"
            + "</script>";
    }
}
