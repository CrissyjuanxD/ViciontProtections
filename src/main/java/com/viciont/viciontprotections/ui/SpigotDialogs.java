package com.viciont.viciontprotections.ui;

import com.google.gson.*;
import java.util.*;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.dialog.*;
import net.md_5.bungee.api.dialog.action.*;
import net.md_5.bungee.api.dialog.body.*;
import net.md_5.bungee.api.dialog.input.*;
import net.md_5.bungee.chat.ComponentSerializer;

/**
 * Convierte los diálogos JSON del plugin en la API de diálogos de BungeeCord que usa Spigot
 * 1.21.6+. Solo se carga en esos servidores; su serializador no admite leer cuerpos ni campos.
 */
final class SpigotDialogs {
  private SpigotDialogs() {}

  static Dialog convert(JsonObject json) {
    List<DialogBody> body = new ArrayList<>();
    for (JsonElement element : array(json, "body")) {
      JsonObject item = element.getAsJsonObject();
      body.add(new PlainMessageBody(component(item.get("contents")), integer(item, "width")));
    }
    List<DialogInput> inputs = new ArrayList<>();
    for (JsonElement element : array(json, "inputs")) {
      JsonObject input = element.getAsJsonObject();
      String key = input.get("key").getAsString();
      BaseComponent label = component(input.get("label"));
      if (input.get("type").getAsString().endsWith("single_option")) {
        List<InputOption> options = new ArrayList<>();
        for (JsonElement value : array(input, "options")) {
          JsonObject option = value.getAsJsonObject();
          options.add(
              new InputOption(
                  option.get("id").getAsString(),
                  option.has("display") ? component(option.get("display")) : null,
                  option.has("initial") ? option.get("initial").getAsBoolean() : null));
        }
        inputs.add(new SingleOptionInput(key, integer(input, "width"), label, true, options));
      } else
        inputs.add(
            new TextInput(
                key,
                integer(input, "width"),
                label,
                true,
                input.has("initial") ? input.get("initial").getAsString() : "",
                integer(input, "max_length")));
    }
    DialogBase base =
        new DialogBase(
            component(json.get("title")),
            null,
            inputs.isEmpty() ? null : inputs,
            body,
            json.has("can_close_with_escape") ? json.get("can_close_with_escape").getAsBoolean() : true,
            false,
            null);
    if (json.get("type").getAsString().endsWith("confirmation"))
      return new ConfirmationDialog(
          base, button(json.getAsJsonObject("yes")), button(json.getAsJsonObject("no")));
    List<ActionButton> actions = new ArrayList<>();
    for (JsonElement element : array(json, "actions")) actions.add(button(element.getAsJsonObject()));
    return new MultiActionDialog(
        base,
        actions,
        integer(json, "columns"),
        json.has("exit_action") ? button(json.getAsJsonObject("exit_action")) : null);
  }

  private static ActionButton button(JsonObject json) {
    // BungeeCord exige una acción: «Cerrar» ejecuta un subcomando que no hace nada.
    Action action = new StaticAction(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pr cerrar"));
    if (json.has("action")) {
      JsonObject click = json.getAsJsonObject("action");
      String type = click.get("type").getAsString().replace("minecraft:", "");
      action =
          switch (type) {
            case "dynamic/run_command" -> new RunCommandAction(click.get("template").getAsString());
            case "open_url" ->
                new StaticAction(new ClickEvent(ClickEvent.Action.OPEN_URL, click.get("url").getAsString()));
            default ->
                new StaticAction(
                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, click.get("command").getAsString()));
          };
    }
    return new ActionButton(
        component(json.get("label")),
        json.has("tooltip") ? component(json.get("tooltip")) : null,
        integer(json, "width"),
        action);
  }

  private static BaseComponent component(JsonElement json) {
    return new TextComponent(ComponentSerializer.parse(json.toString()));
  }

  private static Integer integer(JsonObject json, String key) {
    return json.has(key) ? json.get(key).getAsInt() : null;
  }

  private static JsonArray array(JsonObject json, String key) {
    return json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
  }
}
