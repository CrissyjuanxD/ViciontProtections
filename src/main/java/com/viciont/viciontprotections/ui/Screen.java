package com.viciont.viciontprotections.ui;

import java.util.*;

/**
 * Pantalla de menú independiente de cómo se muestra: como diálogo desde 1.21.6 o como mensajes de
 * chat con botones pulsables en versiones anteriores. Los textos usan la paleta de quien la
 * muestra; las líneas {@link #raw} ya llevan su formato (por ejemplo, mensajes del chat).
 */
public final class Screen {
  /**
   * Botón. Con {@code template}, el comando lleva los valores de los campos ({@code $(clave)}); en
   * el chat se convierte en una sugerencia ({@code suggestion}) para completarla a mano.
   */
  public record Button(String label, String tooltip, String command, boolean template, String suggestion) {}

  /** Campo de texto o, con {@code options} ({clave, texto}), selector de una opción. */
  public record Input(String key, String label, String initial, int max, List<String[]> options) {}

  record Line(String text, boolean raw) {}

  final String title;
  final List<Line> body = new ArrayList<>();
  final List<Button> buttons = new ArrayList<>();
  final List<Input> inputs = new ArrayList<>();
  /** Página a la que lleva «◀ Volver», justo encima de «✖ Salir»; nula en las portadas. */
  Button back;
  int columns = 2;
  boolean plainTitle;

  public Screen(String title) {
    this.title = title;
  }

  /** Título tal cual, sin el degradado por defecto (por ejemplo, con el color de un clan). */
  public Screen plainTitle() {
    plainTitle = true;
    return this;
  }

  public Screen line(String text) {
    body.add(new Line(text, false));
    return this;
  }

  public Screen lines(Collection<String> texts) {
    texts.forEach(this::line);
    return this;
  }

  /** Línea con formato ya aplicado, que no se vuelve a colorear. */
  public Screen raw(String text) {
    body.add(new Line(text, true));
    return this;
  }

  /** Resultado de la última acción, arriba del todo; nada si es nulo. */
  public Screen notice(String text) {
    if (text != null && !text.isBlank()) body.add(0, new Line(text, false));
    return this;
  }

  public Screen button(String label, String tooltip, String command) {
    buttons.add(new Button(label, tooltip, command, false, null));
    return this;
  }

  public Screen submit(String label, String template, String suggestion) {
    buttons.add(new Button(label, null, template, true, suggestion));
    return this;
  }

  public Screen text(String key, String label, String initial, int max) {
    inputs.add(new Input(key, label, initial == null ? "" : initial, max, null));
    return this;
  }

  public Screen options(String key, String label, List<String[]> values) {
    inputs.add(new Input(key, label, "", 0, List.copyOf(values)));
    return this;
  }

  /**
   * Página anterior. Todas las pantallas acaban igual: sus botones, «◀ Volver» a esta página y, en
   * el pie, «✖ Salir» (lo mismo que ESC), así nadie tiene que buscar cómo volver.
   */
  public Screen back(String command) {
    return back(command, null);
  }

  public Screen back(String command, String tooltip) {
    back = new Button("{titulo}◀ {texto}Volver", tooltip, command, false, null);
    return this;
  }

  public Screen columns(int value) {
    columns = value;
    return this;
  }

  public boolean hasButtons() {
    return !buttons.isEmpty();
  }
}
