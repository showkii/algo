/*
+----------------------------------------------------------------------+
| BasicBot                                                             |
+----------------------------------------------------------------------+
| Samsung SDS - 2017 Algorithm Contest                                 |
+----------------------------------------------------------------------+
|                                                                      |
+----------------------------------------------------------------------+
| Author: Tekseon Shin  <tekseon.shin@gmail.com>                       |
| Author: Duckhwan Kim  <duckhwan1982.kim@gmail.com>                   |
+----------------------------------------------------------------------+
*/

/*
+----------------------------------------------------------------------+
| UAlbertaBot                                                          |
+----------------------------------------------------------------------+
| University of Alberta - AIIDE StarCraft Competition                  |
+----------------------------------------------------------------------+
|                                                                      |
+----------------------------------------------------------------------+
| Author: David Churchill <dave.churchill@gmail.com>                   |
+----------------------------------------------------------------------+
*/

import bwapi.Color;
import bwapi.DefaultBWListener;
import bwapi.Game;
import bwapi.Mirror;
import bwapi.Player;
import bwapi.Position;
import bwapi.Unit;
import bwapi.Flag.Enum;
import bwta.BWTA;

/// 봇프로그램의 기본적인 뼈대 구조를 정의한 class 로서, 스타크래프트 대결 도중 발생하는 이벤트들을 GameCommander class 인스턴스에게 전달합니다.<br>
/// MyBotModule class는 수정을 하지 말고,<br>
/// 실제 봇프로그램 개발은 GameCommander class 를 수정하는 형태로 진행하도록 합니다
/// @see GameCommander
public class MyBotModule extends DefaultBWListener {

	/// BWAPI 에 해당하는 내부 객체
	private Mirror mirror = new Mirror();
	
	/// 스타크래프트 대결 상황 전체에 대한 상황 파악 및 액션 실행을 제공하는 객체  <br>
	/// C언어에서 BWAPI::Broodwar 에 해당합니다
	public static Game Broodwar;

	/// 실제 봇프로그램
	/// @see GameCommander			
	private GameCommander gameCommander;

	public void run() {
		mirror.getModule().setEventListener(this);
		mirror.startGame();
	}

	/// 경기가 시작될 때 일회적으로 발생하는 이벤트를 처리합니다
	@Override
	public void onStart() {

		Broodwar = mirror.getGame();
		
		if (Broodwar.isReplay()) {
			return;
		}

		gameCommander = new GameCommander();
		
		// Config 파일 관리가 번거롭고, 배포 및 사용시 Config 파일 위치를 지정해주는 것이 번거롭기 때문에, 
		// Config 를 파일로부터 읽어들이지 않고, Config 클래스의 값을 사용하도록 한다.
		if(Config.EnableCompleteMapInformation){
			Broodwar.enableFlag(Enum.CompleteMapInformation.getValue());
		}

		if(Config.EnableUserInput){
			Broodwar.enableFlag(Enum.UserInput.getValue());
		}

		Broodwar.setCommandOptimizationLevel(1);

		// Speedups for automated play, sets the number of milliseconds bwapi spends in each frame
		// Fastest: 42 ms/frame.  1초에 24 frame. 일반적으로 1초에 24frame을 기준 게임속도로 한다
		// Normal: 67 ms/frame. 1초에 15 frame
		// As fast as possible : 0 ms/frame. CPU가 할수있는 가장 빠른 속도. 
		Broodwar.setLocalSpeed(Config.SetLocalSpeed);
		// frameskip을 늘리면 화면 표시도 업데이트 안하므로 훨씬 빠르다
		Broodwar.setFrameSkip(Config.SetFrameSkip);

		System.out.println("Map analyzing started");
		BWTA.readMap();
		BWTA.analyze();
		BWTA.buildChokeNodes();
		System.out.println("Map analyzing finished");

		gameCommander.onStart();
	}

	///  경기가 종료될 때 일회적으로 발생하는 이벤트를 처리합니다
	@Override
	public void onEnd(boolean isWinner) {
		if (isWinner){
			System.out.println("I won the game");
		} else {
			System.out.println("I lost the game");
		}

		gameCommander.onEnd(isWinner);
		
        System.out.println("Match ended");
        System.exit(0);		
	}

	/// 경기 진행 중 매 프레임마다 발생하는 이벤트를 처리합니다
	@Override
	public void onFrame() {
		if (Broodwar.isReplay()) {
			return;
		}

		gameCommander.onFrame();
		
		// 화면 출력 및 사용자 입력 처리
		UXManager.Instance().update();
	}

	/// 텍스트를 입력 후 엔터를 하여 다른 플레이어들에게 텍스트를 전달하려 할 때 발생하는 이벤트를 처리합니다
	@Override
	public void onSendText(String text){
		ParseTextCommand(text);
		
		gameCommander.onSendText(text);

		// Display the text to the game
		Broodwar.sendText(text);
	}

	/// 다른 플레이어로부터 텍스트를 전달받았을 때 발생하는 이벤트를 처리합니다
	@Override
	public void onReceiveText(Player player, String text){
		Broodwar.printf(player.getName() + " said \"" + text + "\"");

		gameCommander.onReceiveText(player, text);
	}

	/// 다른 플레이어가 대결을 나갔을 때 발생하는 이벤트를 처리합니다
	@Override
	public void onPlayerLeft(Player player){
		Broodwar.printf(player.getName() + " left the game.");
	}

	/// 핵미사일 발사가 감지되었을 때 발생하는 이벤트를 처리합니다
	@Override
	public void onNukeDetect(Position target){
		if (target != Position.Unknown)	{
			Broodwar.drawCircleMap(target, 40, Color.Red, true);
			Broodwar.printf("Nuclear Launch Detected at " + target);
		} else {
			Broodwar.printf("Nuclear Launch Detected");
		}
	}

	/// 유닛(건물/지상유닛/공중유닛)이 Create 될 때 발생하는 이벤트를 처리합니다
	@Override
	public void onUnitCreate(Unit unit){
		if (!Broodwar.isReplay()) {
			gameCommander.onUnitCreate(unit);
		} else {
			// if we are in a replay, then we will print out the build order
			// (just of the buildings, not the units).
			if (unit.getType().isBuilding() && unit.getPlayer().isNeutral() == false) {
				int seconds = Broodwar.getFrameCount() / 24;
				int minutes = seconds / 60;
				seconds %= 60;
				Broodwar.sendText(minutes + ":" + seconds + ": " +  unit.getPlayer().getName() + " creates a " + unit.getType());
			}
		}
	}

	/// 유닛(건물/지상유닛/공중유닛)이 Morph 될 때 발생하는 이벤트를 처리합니다<br>
	/// Zerg 종족의 유닛은 건물 건설이나 지상유닛/공중유닛 생산에서 거의 대부분 Morph 형태로 진행됩니다
	@Override
	public void onUnitMorph(Unit unit){
		if (!Broodwar.isReplay()) {
			gameCommander.onUnitMorph(unit);
		} else {
			// if we are in a replay, then we will print out the build order
			// (just of the buildings, not the units).
			if (unit.getType().isBuilding() && unit.getPlayer().isNeutral() == false) {
				int seconds = Broodwar.getFrameCount() / 24;
				int minutes = seconds / 60;
				seconds %= 60;
				Broodwar.sendText(minutes + ":" + seconds + ": " + unit.getPlayer().getName() + " morphs a " + unit.getType());
			}
		}
	}

	///  유닛(건물/지상유닛/공중유닛)이 Destroy 될 때 발생하는 이벤트를 처리합니다
	@Override
	public void onUnitDestroy(Unit unit){
		if (!Broodwar.isReplay()) {
			/*
			// Use Map Settings 에도 잘 작동하도록 수정 필요
			// 패배 여부 체크 후 GG
			int buildingCount = 0;
			int workerCount = 0;

			for (Unit u : MyBotModule.Broodwar.self().getUnits()) {
				if (u.getType().isBuilding()) {
					buildingCount++;
				}
				else if (u.getType().isWorker()) {
					workerCount++;
				}
			}
			if (buildingCount == 0) {
				Broodwar.sendText("GG");
				Broodwar.leaveGame();
			}
			*/
			
			gameCommander.onUnitDestroy(unit);
		}
	}

	/// 유닛(건물/지상유닛/공중유닛)이 Show 될 때 발생하는 이벤트를 처리합니다<br>
	/// 아군 유닛이 Create 되었을 때 라든가, 적군 유닛이 Discover 되었을 때 발생합니다
	@Override
	public void onUnitShow(Unit unit){
		if (!Broodwar.isReplay()) {
			gameCommander.onUnitShow(unit);
		}
	}

	/// 유닛(건물/지상유닛/공중유닛)이 Hide 될 때 발생하는 이벤트를 처리합니다<br>
	/// 보이던 유닛이 Hide 될 때 발생합니다
	@Override
	public void onUnitHide(Unit unit){
		if (!Broodwar.isReplay()) {
			gameCommander.onUnitHide(unit);
		}
	}

	/// 유닛(건물/지상유닛/공중유닛)의 소속 플레이어가 바뀔 때 발생하는 이벤트를 처리합니다<br>
	/// Gas Geyser에 어떤 플레이어가 Refinery 건물을 건설했을 때, Refinery 건물이 파괴되었을 때, Protoss 종족 Dark Archon 의 Mind Control 에 의해 소속 플레이어가 바뀔 때 발생합니다
	@Override
	public void onUnitRenegade(Unit unit){
		if (!Broodwar.isReplay()) {
			gameCommander.onUnitRenegade(unit);
		}
	}

	/// 유닛(건물/지상유닛/공중유닛)이 Discover 될 때 발생하는 이벤트를 처리합니다<br>
	/// 아군 유닛이 Create 되었을 때 라든가, 적군 유닛이 Discover 되었을 때 발생합니다
	@Override
	public void onUnitDiscover(Unit unit){
		if (!Broodwar.isReplay()) {
			gameCommander.onUnitDiscover(unit);
		}
	}

	/// 유닛(건물/지상유닛/공중유닛)이 Evade 될 때 발생하는 이벤트를 처리합니다<br>
	/// 유닛이 Destroy 될 때 발생합니다
	@Override
	public void onUnitEvade(Unit unit){
		if (!Broodwar.isReplay()) {
			gameCommander.onUnitEvade(unit);
		}
	}

	/// 유닛(건물/지상유닛/공중유닛)의 하던 일 (건물 건설, 업그레이드, 지상유닛 훈련 등)이 끝났을 때 발생하는 이벤트를 처리합니다
	@Override
	public void onUnitComplete(Unit unit){
		if (!Broodwar.isReplay()) {
			gameCommander.onUnitComplete(unit);
		}
	}

	/// 게임을 저장할 때 발생하는 이벤트를 처리합니다
	@Override
	public void onSaveGame(String gameName){
		Broodwar.printf("The game was saved to \"" + gameName + "\".");
	}


	/// 사용자가 입력한 text 를 parse 해서 처리합니다
	public void ParseTextCommand(String commandString)
	{
		// Make sure to use %s and pass the text as a parameter,
		// otherwise you may run into problems when you use the %(percent) character!
		Player self = Broodwar.self();

		if ("/afap".equals(commandString)) {
			Broodwar.setLocalSpeed(0);
			Broodwar.setFrameSkip(0);
		} else if ("/fast".equals(commandString)) {
			Broodwar.setLocalSpeed(24);
			Broodwar.setFrameSkip(0);
		} else if ("/slow".equals(commandString)) {
			Broodwar.setLocalSpeed(42);
			Broodwar.setFrameSkip(0);
		} else if ("/endthegame".equals(commandString)) {
			// Not needed if using setGUI(false).
			Broodwar.setGUI(false);
		}
	}
	
}