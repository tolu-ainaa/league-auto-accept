; Inno Setup script — builds a per-user installer (no admin needed)
; from the jpackage app image in dist\League Auto Accept.

[Setup]
AppName=League Auto Accept
AppVersion=1.0.0
AppPublisher=Tolu
DefaultDirName={autopf}\League Auto Accept
DefaultGroupName=League Auto Accept
PrivilegesRequired=lowest
UninstallDisplayIcon={app}\League Auto Accept.exe
OutputDir=dist
OutputBaseFilename=LeagueAutoAccept-Setup-1.0.0
SetupIconFile=assets\icon.ico
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
DisableProgramGroupPage=yes

[Tasks]
Name: desktopicon; Description: "Create a &desktop shortcut"
Name: startup; Description: "Start automatically when Windows starts"; Flags: unchecked

[Files]
Source: "dist\League Auto Accept\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{group}\League Auto Accept"; Filename: "{app}\League Auto Accept.exe"
Name: "{autodesktop}\League Auto Accept"; Filename: "{app}\League Auto Accept.exe"; Tasks: desktopicon
Name: "{userstartup}\League Auto Accept"; Filename: "{app}\League Auto Accept.exe"; Tasks: startup

[Run]
Filename: "{app}\League Auto Accept.exe"; Description: "Launch League Auto Accept"; Flags: nowait postinstall skipifsilent
