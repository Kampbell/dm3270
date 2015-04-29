package com.bytezone.dm3270.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.prefs.Preferences;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.session.Session;

public class Console extends Application
{
  private static final int MAINFRAME_EMULATOR_PORT = 5555;
  private static final Site DEFAULT_MAINFRAME = new Site ("mainframe", "localhost",
      MAINFRAME_EMULATOR_PORT, true);

  private Stage primaryStage;

  private Preferences prefs;
  private Screen screen;

  private OptionStage optionStage;
  private SpyPane spyPane;
  private ConsolePane consolePane;
  private ReplayStage replayStage;
  private MainframeStage mainframeStage;

  public enum Function
  {
    SPY, REPLAY, TERMINAL, TEST
  }

  @Override
  public void init () throws Exception
  {
    super.init ();

    prefs = Preferences.userNodeForPackage (this.getClass ());
    for (String raw : getParameters ().getRaw ())
      if (raw.equalsIgnoreCase ("-reset"))
        prefs.clear ();

    if (false)
    {
      String[] keys = prefs.keys ();
      Arrays.sort (keys);
      for (String key : keys)
        System.out.printf ("%-14s : %s%n", key, prefs.get (key, ""));
    }
  }

  @Override
  public void start (Stage primaryStagex) throws Exception
  {
    this.primaryStage = primaryStagex;
    optionStage = new OptionStage (prefs);

    optionStage.okButton.setOnAction (e -> startSelectedFunction ());
    optionStage.cancelButton.setOnAction (e -> primaryStage.hide ());
    optionStage.show ();
  }

  private void startSelectedFunction ()
  {
    optionStage.hide ();
    String errorMessage = "";

    Site serverSite = optionStage.serverSitesListStage.getSelectedSite ();
    Site clientSite = optionStage.clientSitesListStage.getSelectedSite ();

    String optionText =
        (String) optionStage.functionsGroup.getSelectedToggle ().getUserData ();
    switch (optionText)
    {
      case "Replay":
        Path path =
            Paths
                .get (optionStage.spyFolder + "/" + optionStage.fileComboBox.getValue ());
        if (!Files.exists (path))
          errorMessage = path + " does not exist";
        else
          try
          {
            Screen screen = createScreen (Function.REPLAY);
            Session session = new Session (screen, path);     // can throw Exception
            setConsolePane (screen);                          // reassigns primaryStage

            replayStage = new ReplayStage (session, path, prefs);
            replayStage.show ();
          }
          catch (Exception e)
          {
            e.printStackTrace ();
            errorMessage = "Error reading file";
          }

        break;

      case "Terminal":
        if (serverSite == null)
          errorMessage = "No server selected";
        else
        {
          setConsolePane (createScreen (Function.TERMINAL));
          consolePane.connect (serverSite);
        }

        break;

      case "Spy":
        if (serverSite == null)
          errorMessage = "No server selected";
        else if (clientSite == null)
          errorMessage = "No client selected";
        else
          setSpyPane (createScreen (Function.SPY), serverSite, clientSite);

        break;

      case "Test":
        if (clientSite == null)
          errorMessage = "No client selected";
        else
        {
          setSpyPane (createScreen (Function.TEST), DEFAULT_MAINFRAME, clientSite);
          mainframeStage = new MainframeStage (MAINFRAME_EMULATOR_PORT);
          mainframeStage.show ();
          mainframeStage.startServer ();
        }

        break;
    }

    if (!errorMessage.isEmpty () && showAlert (errorMessage))
      primaryStage.show ();
  }

  private void setConsolePane (Screen screen)
  {
    consolePane = new ConsolePane (screen, prefs);
    Scene scene = new Scene (consolePane);

    primaryStage.setScene (scene);
    primaryStage.sizeToScene ();
    primaryStage.setX (0);
    primaryStage.setY (0);
    primaryStage.setTitle ("dm3270");

    scene.setOnKeyPressed (new ConsoleKeyPress (consolePane, screen));
    scene.setOnKeyTyped (new ConsoleKeyEvent (screen));

    //    if (SYSTEM_MENUBAR)
    //      menuBar.useSystemMenuBarProperty ().set (true);

    if (screen.getFunction () == Function.TERMINAL)
      primaryStage.centerOnScreen ();

    primaryStage.show ();
  }

  private void setSpyPane (Screen screen, Site server, Site client)
  {
    spyPane = new SpyPane (screen, server, client);
    Scene scene = new Scene (spyPane);

    primaryStage.setScene (scene);
    primaryStage.sizeToScene ();
    primaryStage.setX (0);
    primaryStage.setY (0);
    primaryStage.setTitle ("Terminal Spy");

    primaryStage.show ();
    spyPane.startServer ();
  }

  private boolean showAlert (String message)
  {
    Alert alert = new Alert (AlertType.ERROR, message);
    alert.getDialogPane ().setHeaderText (null);
    Optional<ButtonType> result = alert.showAndWait ();
    return (result.isPresent () && result.get () == ButtonType.OK);
  }

  @Override
  public void stop ()
  {
    if (mainframeStage != null)
      mainframeStage.disconnect ();

    if (spyPane != null)
      spyPane.disconnect ();

    if (consolePane != null)
      consolePane.disconnect ();

    if (replayStage != null)
      replayStage.disconnect ();

    savePreferences ();
  }

  private void savePreferences ()
  {
    prefs.put ("Function", (String) optionStage.functionsGroup.getSelectedToggle ()
        .getUserData ());

    if (screen != null)
    {
      //      Font font = screen.getFont ();
      prefs.put ("FontName", screen.getFontName ());
      prefs.put ("FontSize", "" + screen.getFontSize ());
    }

    prefs
        .put ("Mode", optionStage.toggleModeMenuItem.isSelected () ? "Release" : "Debug");

    String filename = optionStage.fileComboBox.getValue ();
    if (filename != null)
      prefs.put ("ReplayFile", filename);

    prefs.put ("SpyFolder", optionStage.spyFolder);
    prefs.put ("ServerName", optionStage.serverComboBox.getSelectionModel ()
        .getSelectedItem ());
    prefs.put ("ClientName", optionStage.clientComboBox.getSelectionModel ()
        .getSelectedItem ());
  }

  private Screen createScreen (Function function)
  {
    //    RadioMenuItem selectedFontName =
    //        (RadioMenuItem) consolePane.fontGroup.getSelectedToggle ();
    //    RadioMenuItem selectedFontSize =
    //        (RadioMenuItem) consolePane.sizeGroup.getSelectedToggle ();
    //    Font font = Font.font (selectedFontName.getText (),     //
    //                           Integer.parseInt (selectedFontSize.getText ()));
    screen = new Screen (24, 80, prefs, function);
    return screen;
  }
}