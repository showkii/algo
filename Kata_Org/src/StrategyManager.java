import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import bwapi.Race;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Chokepoint;

/// 상황을 판단하여, 정찰, 빌드, 공격, 방어 등을 수행하도록 총괄 지휘를 하는 class <br>
/// InformationManager 에 있는 정보들로부터 상황을 판단하고, <br>
/// BuildManager 의 buildQueue에 빌드 (건물 건설 / 유닛 훈련 / 테크 리서치 / 업그레이드) 명령을 입력합니다.<br>
/// 정찰, 빌드, 공격, 방어 등을 수행하는 코드가 들어가는 class
public class StrategyManager {

	private static StrategyManager instance = new StrategyManager();

	private CommandUtil commandUtil = new CommandUtil();

	private boolean isFullScaleAttackStarted;
	private boolean isInitialBuildOrderFinished;
	
	
	
	//2017-07-11 Added.by yong.
	private int curStrategyStep;
	private int curConstructionStep;
	//2017-07-14 Added.by yong.
	private HashMap<Integer, Integer> attackingGroup ;
	
	//2017-07-19 Added.by hoon.
	private final int BASIC_STEP = 0;
	private final int ADVAN_STEP = 1;
	private final int MULTI_STEP = 2;
	private final int PRO_STEP = 3;
	
	// 최소 요구 건물수 등록
	public List<Map> requiredConstructionByStrategy = new ArrayList<>();
	
	
	/// static singleton 객체를 리턴합니다
	public static StrategyManager Instance() {
		return instance;
	}

	public StrategyManager() {
		isFullScaleAttackStarted = false;
		isInitialBuildOrderFinished = false;
		
		curStrategyStep=0;
		curConstructionStep=0;
		attackingGroup = new HashMap<>();	//공격유닛의 부대 지정, 기준은 curStrategyStep
		
		// basic
		Map<UnitType, Integer> requiredConstructionBasicMap = new HashMap<>();
		requiredConstructionBasicMap.put(UnitType.Protoss_Gateway, 2);

		requiredConstructionByStrategy.add(requiredConstructionBasicMap);

		// advan
		Map<UnitType, Integer> requiredConstructionAdvMap = new HashMap<>();
		requiredConstructionAdvMap.put(UnitType.Protoss_Cybernetics_Core, 1);
		requiredConstructionAdvMap.put(UnitType.Protoss_Gateway, 3);
		requiredConstructionAdvMap.put(UnitType.Protoss_Assimilator, 1);

		requiredConstructionByStrategy.add(requiredConstructionAdvMap);
		
		// multi
		Map<UnitType, Integer> requiredConstructionMultiMap = new HashMap<>();
		
		requiredConstructionMultiMap.put(UnitType.Protoss_Cybernetics_Core, 1);
		requiredConstructionMultiMap.put(UnitType.Protoss_Gateway, 3);
		requiredConstructionMultiMap.put(UnitType.Protoss_Assimilator, 1);
// todo
		
		requiredConstructionByStrategy.add(requiredConstructionMultiMap);
		
		// pro
		Map<UnitType, Integer> requiredConstructionProMap = new HashMap<>();
		requiredConstructionProMap.put(UnitType.Protoss_Cybernetics_Core, 1);
		requiredConstructionProMap.put(UnitType.Protoss_Gateway, 3);
		requiredConstructionProMap.put(UnitType.Protoss_Assimilator, 1);

		requiredConstructionByStrategy.add(requiredConstructionProMap);
		
	}

	/// 경기가 시작될 때 일회적으로 전략 초기 세팅 관련 로직을 실행합니다
	public void onStart() {
		setInitialBuildOrder();		
	}

	///  경기가 종료될 때 일회적으로 전략 결과 정리 관련 로직을 실행합니다
	public void onEnd(boolean isWinner) {

	}

	/// 경기 진행 중 매 프레임마다 경기 전략 관련 로직을 실행합니다
	public void update() {
		if (BuildManager.Instance().buildQueue.isEmpty()) {
			isInitialBuildOrderFinished = true;
		}

		executeWorkerTraining();

		executeSupplyManagement();

		
		switch(curStrategyStep)
		{
			case BASIC_STEP:
				executeBasicCombatUnitTraining();
				
				executeCombat();
				
				break;
			case ADVAN_STEP:
				
				executeAdvancedConstructorManagement();
				
				executeAdvancedCombatUnitTraining();

				executeCombat();
				
				break;
			case MULTI_STEP:
				executeMultiConstructorManagement();
				
				executeAdvancedCombatUnitTraining();
				
				//멀티가 완성되면(Nexus 가 2개) 본진의 일꾼 반을 보낸다. 
				
				
				executeCombat();
				break;
			case PRO_STEP:
				//executeProConstructorManagement();
				
				executeAdvancedCombatUnitTraining();
				
				//멀티가 완성되면(Nexus 가 2개) 본진의 일꾼 반을 보낸다. 
				
				
				executeCombat();
			
				break;
		}
	}

	private void executeAdvancedConstructorManagement() {
		// TODO Auto-generated method stub
		//1차 러시가 하지 않고, 질럿이 4기 모이면 수행
		//if(curAttackCount == 0 && MyBotModule.Broodwar.self().completedUnitCount(InformationManager.Instance().getBasicCombatUnitType()) < 4){
		if(curStrategyStep!=ADVAN_STEP) {
			return;
		}
		
		//건설 flag 는 전략 flag 와 별도로 관리됨(딱 한 번 수행)
		if(curConstructionStep==BASIC_STEP){
			
			addQueueBeforeStepConstructionRequired(BASIC_STEP, BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			
			
			if(MyBotModule.Broodwar.self().completedUnitCount(UnitType.Protoss_Cybernetics_Core) == 0){
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Cybernetics_Core,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Assimilator,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
				
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Zealot,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Zealot,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				
				BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Gateway,
						BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
				
			}
				curConstructionStep=ADVAN_STEP;
		}
	}

	private void addQueueBeforeStepConstructionRequired(int step,BuildOrderItem.SeedPositionStrategy seedPositionStrategy) {
		// 이전 스텝에 빠진 건물 큐 넣기
		Map map = requiredConstructionByStrategy.get(step);
		Iterator<UnitType> it = map.keySet().iterator();
		while(it.hasNext()){
			UnitType type = (UnitType)it.next();
			int currentUnitCnt = InformationManager.Instance().getNumUnits(type, InformationManager.Instance().selfPlayer);
			System.out.println("Unittype :" + type);
			System.out.println("currentUnitCnt :" + currentUnitCnt);
			
			if((Integer)map.get(type) > currentUnitCnt){
				for(int i=0; i < ((Integer)map.get(type) - currentUnitCnt); i++){
					BuildManager.Instance().buildQueue.queueAsLowestPriority(type, 
							seedPositionStrategy);
				}
			}
		}
	}
	
	private void executeMultiConstructorManagement() {
		// TODO Auto-generated method stub
		//if(curAttackCount == 0 && MyBotModule.Broodwar.self().completedUnitCount(InformationManager.Instance().getBasicCombatUnitType()) < 4){
		if(curStrategyStep!=MULTI_STEP) {
			return;
		}
	
		if(curConstructionStep==ADVAN_STEP){
			addQueueBeforeStepConstructionRequired(ADVAN_STEP, BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			
			//앞마당 멀티
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Nexus,
					BuildOrderItem.SeedPositionStrategy.FirstExpansionLocation);
			//로보틱스 퍼실리티(옵저버용)
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Robotics_Facility,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Observatory);
			//업그레이드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Forge,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Gateway,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			//드라군 사업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Singularity_Charge);
			//공업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Protoss_Ground_Weapons);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Observer);
				
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Zealot,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Zealot,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Zealot,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			
			curConstructionStep=MULTI_STEP;
		}
	}

	public void setInitialBuildOrder() {
		if (MyBotModule.Broodwar.self().getRace() == Race.Protoss) {
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(
					InformationManager.Instance().getBasicSupplyProviderUnitType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Gateway,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Zealot,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Gateway,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			
			
			
			/*
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Assimilator,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Forge,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Photon_Cannon,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Gateway,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Zealot);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Cybernetics_Core,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Dragoon);
			// 드라군 사정거리 업그레이드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Singularity_Charge);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Citadel_of_Adun);
			// 질럿 속도 업그레이드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Leg_Enhancements);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Shield_Battery);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Templar_Archives);
			// 하이템플러
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_High_Templar);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_High_Templar);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Psionic_Storm);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Hallucination);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Khaydarin_Amulet);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Archon);

			// 다크아칸
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Dark_Templar);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Dark_Templar);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Maelstrom);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Mind_Control);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Argus_Talisman);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Dark_Archon);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Robotics_Facility);

			// 셔틀
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Shuttle);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Robotics_Support_Bay);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Gravitic_Drive);

			// 리버
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Reaver);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Scarab_Damage);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Reaver_Capacity);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Scarab);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Observatory);
			// 옵저버
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Observer);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Gravitic_Boosters);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Sensor_Array);

			// 공중유닛
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Stargate);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Fleet_Beacon);

			// 스카우트
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Scout);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Apial_Sensors);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Gravitic_Thrusters);

			// 커세어
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Corsair);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Disruption_Web);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Argus_Jewel);

			// 캐리어
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Carrier);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Carrier_Capacity);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Interceptor);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Interceptor);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Interceptor);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Interceptor);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Interceptor);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Interceptor);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Interceptor);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Interceptor);

			// 아비터
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Arbiter_Tribunal);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Protoss_Arbiter);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Recall);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Stasis_Field);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Khaydarin_Core);

			// 포지 - 지상 유닛 업그레이드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Protoss_Ground_Weapons);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Protoss_Plasma_Shields);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Protoss_Ground_Armor);

			// 사이버네틱스코어 - 공중 유닛 업그레이드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Protoss_Air_Weapons);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Protoss_Air_Armor);

			*/
		} 
		else if (MyBotModule.Broodwar.self().getRace() == Race.Terran) {
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(
					InformationManager.Instance().getBasicSupplyProviderUnitType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Barracks,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);

			/*
			// 가스 리파이너리
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getRefineryBuildingType());

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Barracks,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Bunker,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Marine);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Academy);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Comsat_Station);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Medic);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Firebat);

			// 지상유닛 업그레이드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Engineering_Bay);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Terran_Infantry_Weapons, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Terran_Infantry_Armor, false);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Missile_Turret);

			// 마린 스팀팩
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Stim_Packs, false);
			// 마린 사정거리 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.U_238_Shells, false);

			// 메딕
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Optical_Flare, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Restoration, false);
			// 메딕 에너지 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Caduceus_Reactor, false);

			// 팩토리
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Factory);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Machine_Shop);
			// 벌쳐 스파이더 마인
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Spider_Mines, false);
			// 벌쳐 이동속도 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Ion_Thrusters, false);
			// 시즈탱크 시즈모드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Siege_Tank_Tank_Mode);

			// 벌쳐
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Vulture);

			// 시즈탱크
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Tank_Siege_Mode, false);

			// 아머니
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Armory);
			// 지상 메카닉 유닛 업그레이드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Terran_Vehicle_Plating, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Terran_Vehicle_Weapons, false);
			// 공중 유닛 업그레이드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Terran_Ship_Plating, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Terran_Ship_Weapons, false);
			// 골리앗 사정거리 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Charon_Boosters, false);

			// 골리앗
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Goliath);

			// 스타포트
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Starport);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Control_Tower);
			// 레이쓰 클러킹
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Cloaking_Field, false);
			// 레이쓰 에너지 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Apollo_Reactor, false);

			// 레이쓰
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Wraith);

			// 발키리
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Valkyrie);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Command_Center);

			// 사이언스 퍼실리티
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Science_Facility);
			// 사이언스 베슬 - 기술
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Irradiate, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.EMP_Shockwave, false);
			// 사이언스 베슬 에너지 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Titan_Reactor, false);

			// 사이언스 베슬
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Science_Vessel);
			// 사이언스 퍼실리티 - 배틀크루저 생산 가능
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Physics_Lab);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Yamato_Gun, false);
			// 배틀크루저 에너지 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Colossus_Reactor, false);
			// 배틀크루저
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Battlecruiser);

			// 사이언스 퍼실리티 - 고스트 생산 가능
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Science_Facility);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Covert_Ops);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Lockdown, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Personnel_Cloaking, false);
			// 고스트 가시거리 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Ocular_Implants, false);
			// 고스트 에너지 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Moebius_Reactor, false);

			// 고스트
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Ghost);

			// 핵폭탄
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Command_Center);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Nuclear_Silo);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Terran_Nuclear_Missile);
			*/
		} 
		else if (MyBotModule.Broodwar.self().getRace() == Race.Zerg) {
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Spawning_Pool,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Zergling,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Zergling,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Zergling,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getBasicSupplyProviderUnitType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);

			/*
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Hatchery,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Hatchery,
					BuildOrderItem.SeedPositionStrategy.FirstExpansionLocation);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Hatchery,
					BuildOrderItem.SeedPositionStrategy.FirstExpansionLocation);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(
					InformationManager.Instance().getBasicSupplyProviderUnitType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType(),
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);

			// 가스 익스트랙터
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Extractor,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);

			// 성큰 콜로니
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Creep_Colony,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Sunken_Colony,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);

			BuildManager.Instance().buildQueue
					.queueAsLowestPriority(InformationManager.Instance().getRefineryBuildingType());

			// 저글링 이동속도 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Metabolic_Boost);

			// 에볼루션 챔버
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Evolution_Chamber);
			// 에볼루션 챔버 . 지상유닛 업그레이드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Zerg_Melee_Attacks, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Zerg_Missile_Attacks, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Zerg_Carapace, false);

			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType());
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType());
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType());
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType());
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType());
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType());
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType());
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType());
			BuildManager.Instance().buildQueue.queueAsLowestPriority(InformationManager.Instance().getWorkerType());
			
			// 스포어 코로니
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Creep_Colony,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Spore_Colony,
					BuildOrderItem.SeedPositionStrategy.MainBaseLocation);

			// 히드라
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Hydralisk_Den);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Hydralisk);

			// 레어
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Lair);

			// 오버로드 운반가능
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Ventral_Sacs);
			// 오버로드 시야 증가
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Antennae);
			// 오버로드 속도 증가
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Pneumatized_Carapace);

			// 히드라 이동속도 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Muscular_Augments, false);
			// 히드라 공격 사정거리 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Grooved_Spines, false);

			// 럴커
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Lurker_Aspect);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Hydralisk);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Lurker);

			// 스파이어
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Spire, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Mutalisk, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Scourge, true);

			// 스파이어 . 공중유닛 업그레이드
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Zerg_Flyer_Attacks, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Zerg_Flyer_Carapace, false);

			// 퀸
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Queens_Nest);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Queen);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Spawn_Broodlings, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Ensnare, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Gamete_Meiosis, false);

			// 하이브
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Hive);
			// 저글링 공격 속도 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Adrenal_Glands, false);

			// 스파이어 . 그레이트 스파이어
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Greater_Spire, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Mutalisk, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Guardian, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Mutalisk, true);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Devourer, true);

			// 울트라리스크
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Ultralisk_Cavern);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Ultralisk);
			// 울트라리스크 이동속도 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Anabolic_Synthesis, false);
			// 울트라리스크 방어력 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Chitinous_Plating, false);

			// 디파일러
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Defiler_Mound);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Defiler);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Consume, false);
			BuildManager.Instance().buildQueue.queueAsLowestPriority(TechType.Plague, false);
			// 디파일러 에너지 업
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UpgradeType.Metasynaptic_Node, false);

			// 나이더스 캐널
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Nydus_Canal);

			// 참고로, Zerg_Nydus_Canal 건물로부터 Nydus Canal Exit를 만드는 방법은 다음과 같습니다
			//if (MyBotModule.Broodwar.self().completedUnitCount(UnitType.Zerg_Nydus_Canal) > 0) {
			//	for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
			//		if (unit.getType() == UnitType.Zerg_Nydus_Canal) {
			//			TilePosition targetTilePosition = new TilePosition(unit.getTilePosition().getX() + 6, unit.getTilePosition().getY()); // Creep 이 있는 곳이어야 한다
			//			unit.build(UnitType.Zerg_Nydus_Canal, targetTilePosition);
			//		}
			//	}
			//}

			// 퀸 - 인페스티드 테란 : 테란 Terran_Command_Center 건물의 HitPoint가 낮을 때, 퀸을 들여보내서 Zerg_Infested_Command_Center 로 바꾸면, 그 건물에서 실행 됨
			BuildManager.Instance().buildQueue.queueAsLowestPriority(UnitType.Zerg_Infested_Terran);
			*/
		}
	}

	// 일꾼 계속 추가 생산
	public void executeWorkerTraining() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		if (MyBotModule.Broodwar.self().minerals() >= 50) {
			// workerCount = 현재 일꾼 수 + 생산중인 일꾼 수
			int workerCount = MyBotModule.Broodwar.self().allUnitCount(InformationManager.Instance().getWorkerType());

			if (MyBotModule.Broodwar.self().getRace() == Race.Zerg) {
				for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
					if (unit.getType() == UnitType.Zerg_Egg) {
						// Zerg_Egg 에게 morph 명령을 내리면 isMorphing = true,
						// isBeingConstructed = true, isConstructing = true 가 된다
						// Zerg_Egg 가 다른 유닛으로 바뀌면서 새로 만들어진 유닛은 잠시
						// isBeingConstructed = true, isConstructing = true 가
						// 되었다가,
						if (unit.isMorphing() && unit.getBuildType() == UnitType.Zerg_Drone) {
							workerCount++;
						}
					}
				}
			} else {
				for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
					if (unit.getType().isResourceDepot()) {
						if (unit.isTraining()) {
							workerCount += unit.getTrainingQueue().size();
						}
					}
				}
			}

			if (workerCount < 50) {
				for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
					if (unit.getType().isResourceDepot()) {
						if (unit.isTraining() == false || unit.getLarva().size() > 0) {
							// 빌드큐에 일꾼 생산이 1개는 있도록 한다
							if (BuildManager.Instance().buildQueue
									.getItemCount(InformationManager.Instance().getWorkerType(), null) == 0) {
								// std.cout << "worker enqueue" << std.endl;
								BuildManager.Instance().buildQueue.queueAsLowestPriority(
										new MetaType(InformationManager.Instance().getWorkerType()), false);
							}
						}
					}
				}
			}
		}
	}

	// Supply DeadLock 예방 및 SupplyProvider 가 부족해질 상황 에 대한 선제적 대응으로서<br>
	// SupplyProvider를 추가 건설/생산한다
	public void executeSupplyManagement() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 1초에 한번만 실행
		if (MyBotModule.Broodwar.getFrameCount() % 24 != 0) {
			return;
		}

		// 게임에서는 서플라이 값이 200까지 있지만, BWAPI 에서는 서플라이 값이 400까지 있다
		// 저글링 1마리가 게임에서는 서플라이를 0.5 차지하지만, BWAPI 에서는 서플라이를 1 차지한다
		if (MyBotModule.Broodwar.self().supplyTotal() <= 400) {

			// 서플라이가 다 꽉찼을때 새 서플라이를 지으면 지연이 많이 일어나므로, supplyMargin (게임에서의 서플라이 마진 값의 2배)만큼 부족해지면 새 서플라이를 짓도록 한다
			// 이렇게 값을 정해놓으면, 게임 초반부에는 서플라이를 너무 일찍 짓고, 게임 후반부에는 서플라이를 너무 늦게 짓게 된다
			int supplyMargin = 12;

			// currentSupplyShortage 를 계산한다
			int currentSupplyShortage = MyBotModule.Broodwar.self().supplyUsed() + supplyMargin - MyBotModule.Broodwar.self().supplyTotal();

			if (currentSupplyShortage > 0) {
				
				// 생산/건설 중인 Supply를 센다
				int onBuildingSupplyCount = 0;

				// 저그 종족인 경우, 생산중인 Zerg_Overlord (Zerg_Egg) 를 센다. Hatchery 등 건물은 세지 않는다
				if (MyBotModule.Broodwar.self().getRace() == Race.Zerg) {
					for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
						if (unit.getType() == UnitType.Zerg_Egg && unit.getBuildType() == UnitType.Zerg_Overlord) {
							onBuildingSupplyCount += UnitType.Zerg_Overlord.supplyProvided();
						}
						// 갓태어난 Overlord 는 아직 SupplyTotal 에 반영안되어서, 추가 카운트를 해줘야함
						if (unit.getType() == UnitType.Zerg_Overlord && unit.isConstructing()) {
							onBuildingSupplyCount += UnitType.Zerg_Overlord.supplyProvided();
						}
					}
				}
				// 저그 종족이 아닌 경우, 건설중인 Protoss_Pylon, Terran_Supply_Depot 를 센다. Nexus, Command Center 등 건물은 세지 않는다
				else {
					onBuildingSupplyCount += ConstructionManager.Instance().getConstructionQueueItemCount(
							InformationManager.Instance().getBasicSupplyProviderUnitType(), null)
							* InformationManager.Instance().getBasicSupplyProviderUnitType().supplyProvided();
				}

				//System.out.println("currentSupplyShortage : " + currentSupplyShortage + " onBuildingSupplyCount : " + onBuildingSupplyCount);

				if (currentSupplyShortage > onBuildingSupplyCount) {
					
					// BuildQueue 최상단에 SupplyProvider 가 있지 않으면 enqueue 한다
					boolean isToEnqueue = true;
					if (!BuildManager.Instance().buildQueue.isEmpty()) {
						BuildOrderItem currentItem = BuildManager.Instance().buildQueue.getHighestPriorityItem();
						if (currentItem.metaType.isUnit() 
							&& currentItem.metaType.getUnitType() == InformationManager.Instance().getBasicSupplyProviderUnitType()) 
						{
							isToEnqueue = false;
						}
					}
					if (isToEnqueue) {
						System.out.println("enqueue supply provider "
								+ InformationManager.Instance().getBasicSupplyProviderUnitType());
						BuildManager.Instance().buildQueue.queueAsHighestPriority(
								new MetaType(InformationManager.Instance().getBasicSupplyProviderUnitType()), true);
					}
				}
			}
		}
	}

	public void executeBasicCombatUnitTraining() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}

		// 기본 병력 추가 훈련
		if (MyBotModule.Broodwar.self().minerals() >= 130 && MyBotModule.Broodwar.self().supplyUsed() < 390) {
			for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
				if (unit.getType() == InformationManager.Instance().getBasicCombatBuildingType()) {
					if (unit.isTraining() == false || unit.getLarva().size() > 0) {
						if (BuildManager.Instance().buildQueue
								.getItemCount(InformationManager.Instance().getBasicCombatUnitType(), null) == 0) {
							BuildManager.Instance().buildQueue.queueAsLowestPriority(
									InformationManager.Instance().getBasicCombatUnitType(),
									BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
						}
					}
				}
			}
		}
	}
	
	public void executeAdvancedCombatUnitTraining() {

		// InitialBuildOrder 진행중에는 아무것도 하지 않습니다
		if (isInitialBuildOrderFinished == false) {
			return;
		}
		
		//최초 러시 이후에수행
		if(curStrategyStep == BASIC_STEP){
			return;
		}

		// 드라군 병력 추가 훈련
		if (MyBotModule.Broodwar.self().minerals() >= 150 && MyBotModule.Broodwar.self().supplyUsed() < 390) {
			if (BuildManager.Instance().buildQueue
							.getItemCount(InformationManager.Instance().getAdvancedCombatUnitType(), null) <= 5 ) {
						BuildManager.Instance().buildQueue.queueAsLowestPriority(
								InformationManager.Instance().getAdvancedCombatUnitType(),
								BuildOrderItem.SeedPositionStrategy.MainBaseLocation, true);
			}
			
		}
	}


	public void executeCombat() {

		// 공격 모드가 아닐 때에는 전투유닛들을 아군 진영 길목에 집결시켜서 방어
		//if (isFullScaleAttackStarted == false) {
			

			//최초 러시(질럿 6기 러시)
			if(curStrategyStep == BASIC_STEP){
				// 기본 전투 유닛이 5개 이상 생산되었고, 적군 위치가 파악되었으면 총공격 모드로 전환
				if (MyBotModule.Broodwar.self().completedUnitCount(InformationManager.Instance().getBasicCombatUnitType()) > 5) {
					if (InformationManager.Instance().enemyPlayer != null
						&& InformationManager.Instance().enemyRace != Race.Unknown  
						&& InformationManager.Instance().getOccupiedBaseLocations(InformationManager.Instance().enemyPlayer).size() > 0) {				
						//isFullScaleAttackStarted = true;
						System.out.println("BASIC_STEP totalAttack called");
						totalAttack();						
						curStrategyStep=ADVAN_STEP;
					}
				}else{
					Chokepoint firstChokePoint = BWTA.getNearestChokepoint(InformationManager.Instance().getMainBaseLocation(InformationManager.Instance().selfPlayer).getTilePosition());

					for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
						if (unit.getType() == InformationManager.Instance().getBasicCombatUnitType() && unit.isIdle()) {
							commandUtil.attackMove(unit, firstChokePoint.getCenter());
						}
					}
				}
			}else if(curStrategyStep == ADVAN_STEP){
				
				//드라군 5기 이상 되면 공격
				if (MyBotModule.Broodwar.self().completedUnitCount(InformationManager.Instance().getAdvancedCombatUnitType()) > 5) {
					if (InformationManager.Instance().enemyPlayer != null
						&& InformationManager.Instance().enemyRace != Race.Unknown  
						&& InformationManager.Instance().getOccupiedBaseLocations(InformationManager.Instance().enemyPlayer).size() > 0) {				
						//isFullScaleAttackStarted = true;
						System.out.println("ADVAN_STEP totalAttack called");
						totalAttack();
						curStrategyStep=MULTI_STEP;
					}
				}
				else{
					Chokepoint secondChokePoint = InformationManager.Instance().getSecondChokePoint(InformationManager.Instance().selfPlayer);

					for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
						
						//System.out.println("##Unit id : " + unit.getID());
						
						if ((unit.getType() == InformationManager.Instance().getAdvancedCombatUnitType()
								|| unit.getType() == InformationManager.Instance().getBasicCombatUnitType())
								//&& !unit.isAttacking()) 
								&& (!attackingGroup.containsKey(unit.getID()))
								){
							commandUtil.attackMove(unit,  secondChokePoint.getCenter());
						}
					}
				}
				
			}else if(curStrategyStep == MULTI_STEP){
				
				//옵저버가 있을 것
				//드라군 사업이 될 것 UpgradeType.Singularity_Charge
				if (MyBotModule.Broodwar.self().completedUnitCount(UnitType.Protoss_Observer) > 0
						&& MyBotModule.Broodwar.self().getUpgradeLevel(UpgradeType.Singularity_Charge)>0
						) {
					if (InformationManager.Instance().enemyPlayer != null
						&& InformationManager.Instance().enemyRace != Race.Unknown  
						&& InformationManager.Instance().getOccupiedBaseLocations(InformationManager.Instance().enemyPlayer).size() > 0) {				
						//isFullScaleAttackStarted = true;
						System.out.println("MULTI_STEP totalAttack called");
						totalAttack();
						curStrategyStep=PRO_STEP;
						System.out.println(MyBotModule.Broodwar.self().getUpgradeLevel(UpgradeType.Singularity_Charge));
					}
				}
				else{
					Chokepoint secondChokePoint = InformationManager.Instance().getSecondChokePoint(InformationManager.Instance().selfPlayer);

					for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
						
						//System.out.println("##Unit id : " + unit.getID());
						
						if ((unit.getType() == InformationManager.Instance().getAdvancedCombatUnitType()
								|| unit.getType() == InformationManager.Instance().getBasicCombatUnitType()
								|| unit.getType() == UnitType.Protoss_Observer)
								//&& !unit.isAttacking()) 
								&& (!attackingGroup.containsKey(unit.getID()))
								){
							commandUtil.attackMove(unit,  secondChokePoint.getCenter());
						}
					}
				}
				
			}

		//}
		// 공격 모드가 되면, 모든 전투유닛들을 적군 Main BaseLocation 로 공격 가도록 합니다
		//else {
		//	totalAttack();
		//}
	}
	
	
	
	
	//총 공격
	private void totalAttack() {
		//std.cout << "enemy OccupiedBaseLocations : " << InformationManager.Instance().getOccupiedBaseLocations(InformationManager.Instance()._enemy).size() << std.endl;
		
		if (InformationManager.Instance().enemyPlayer != null
				&& InformationManager.Instance().enemyRace != Race.Unknown 
				&& InformationManager.Instance().getOccupiedBaseLocations(InformationManager.Instance().enemyPlayer).size() > 0) 
		{					
			// 공격 대상 지역 결정
			BaseLocation targetBaseLocation = null;
			double closestDistance = 100000000;

			for (BaseLocation baseLocation : InformationManager.Instance().getOccupiedBaseLocations(InformationManager.Instance().enemyPlayer)) {
				double distance = BWTA.getGroundDistance(
					InformationManager.Instance().getMainBaseLocation(InformationManager.Instance().selfPlayer).getTilePosition(), 
					baseLocation.getTilePosition());

				if (distance < closestDistance) {
					closestDistance = distance;
					targetBaseLocation = baseLocation;
				}
			}
			//적의 앞마당 멀티 위치
			BaseLocation enemyFirstExpansionLocation = InformationManager.Instance().getFirstExpansionLocation(InformationManager.Instance().enemyPlayer);
			
			if (targetBaseLocation != null) {
				
				for (Unit unit : MyBotModule.Broodwar.self().getUnits()) {
					// 건물은 제외
					if (unit.getType().isBuilding()) {
						continue;
					}
					// 모든 일꾼은 제외
					if (unit.getType().isWorker()) {
						continue;
					}
										
					// canAttack 유닛은 attackMove Command 로 공격을 보냅니다
					if (unit.canAttack() || unit.getType() == UnitType.Protoss_Observer) {
						
						if (unit.isIdle()) {
							
							//Unit leadingUnit = null;
							
							switch (curStrategyStep) {
							case BASIC_STEP:
								commandUtil.attackMove(unit , enemyFirstExpansionLocation.getPosition());
								break;
							case ADVAN_STEP:
								
								/*if(unit.getType() == UnitType.Protoss_Zealot && leadingUnit == null){
									leadingUnit = unit;
								}
								commandUtil.attackMove(unit , enemyFirstExpansionLocation.getPosition());
								
								if(unit.getType() == UnitType.Protoss_Dragoon && leadingUnit != null){
									unit.follow(leadingUnit);
								}*/
								
								commandUtil.attackMove(unit , enemyFirstExpansionLocation.getPosition());
								
								break;
							case MULTI_STEP:
								if(unit.getType() == UnitType.Protoss_Observer){
									commandUtil.move(unit , enemyFirstExpansionLocation.getPosition());
								}else{
									commandUtil.attackMove(unit , enemyFirstExpansionLocation.getPosition());
								}
								break;
							default:
								commandUtil.attackMove(unit , targetBaseLocation.getPosition());
								if(unit.getType() == UnitType.Protoss_Observer){
									commandUtil.move(unit , enemyFirstExpansionLocation.getPosition());
								}
								break;
							}
							//commandUtil.attackMove(unit , targetBaseLocation.getPosition());
							
							attackingGroup.put(unit.getID(), curStrategyStep);
							//System.out.println("#Unit id : " + unit.getID());
							
						}
					} 
				}
				
			}
		}
	}

	public int getCurConstructionStep(){
		return curConstructionStep;
	}

}