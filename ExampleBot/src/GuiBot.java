import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import bwapi.Color;
import bwapi.DefaultBWListener;
import bwapi.Game;
import bwapi.Mirror;
import bwapi.Player;
import bwapi.Position;
import bwapi.Race;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitCommand;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwapi.WeaponType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Chokepoint;
import bwta.Region;

public class GuiBot extends DefaultBWListener {

    private Mirror mirror = new Mirror();
    
    private Game game;

    private Player self;
    protected static Race enemyRace;
    private HashMap<UnitType, Integer> enemyBuildingTab;
    private HashMap<UnitType, Integer> enemyCompletedBuildingTab;
    private HashMap<UnitType, Integer> enemyArmyTab;
    private HashMap<UnitType, Integer> enemiesKilledTab;
    private HashMap<UnitType, Integer> unitLostTab;
    private HashSet<TechType> enemyResearchTab;
    private HashMap<Integer, HisUnit> enemyBuildings;
    private HashMap<Integer, HisUnit> enemyArmy;
    protected static HashMap<Integer, MyUnit> myUnits;
    private Squad freeAgents;
    private Squad mainArmy;
    private Squad newWorkers;
    private ArrayList<Squad> army;
    private HashMap<Region, MyBase> bases;
    private HashMap<Region, HisBase> enemyBases;
    private int gasBases;
    private int armySupplyKilled;
    private int enemyArmySupplyKilled;
    private int armySupply;
    private int enemyArmySupply;
    
    private Chokepoint defenseChoke;
    private Chokepoint mainChoke;
    private Chokepoint naturalChoke;
    private Chokepoint lateGameChoke;
    private BaseLocation natural;

    private UnitType nextItem;
    private int nextItemX;
    private int nextItemY;
    private int pylonSkip;
    
    public static final int PIXELS_PER_TILE = 32;
    public static final float SECONDS_PER_FRAME = 0.042f;
    
    private float mineralsPerFrame;
    private float gasPerFrame;
    private float supplyPerFrame;
    
    private int reservedMinerals;
    private int reservedGas;
    private int patchCount;
    
    //21 Nexus build
    private UnitType[] selectedBO; 
    private boolean[] BOChecklist;
    private UnitType saveFor;
    private int saveForIndex;
    private int BOProgress;
    private int pullGas;
    private int resumeGas;
    private int gasCap;    
    private boolean buildDone;
    
    private Unit scoutingProbe;
    private TilePosition enemyMain;
    private Position attackPosition;
    private Position airAttackPosition;
    private HisBase dropBase;
    
    //potential maps
    private double[][] visionMap;
    private double[][] gtaDangerMap;
    public static double[][] walkMap;
    
    //output files
	BufferedWriter writer;
    
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }
    
    @Override
    public void onUnitDiscover(Unit unit) {
    	//update enemy building info
    	try {
	    	if(game.getFrameCount() > 0) {
	    		if(unit.getPlayer() == game.enemy()) {
			    	if(unit.getType().isBuilding() || unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode
			    		|| unit.getType() == UnitType.Terran_Vulture_Spider_Mine) {    	
			    		enemyBuildings.put(unit.getID(), new HisUnit(unit, game));
			    	} else if(unit.getType() != UnitType.Zerg_Larva && unit.getType() != UnitType.Zerg_Egg 
			    		&& unit.getType() != UnitType.Protoss_Scarab){
			    		enemyArmy.put(unit.getID(), new HisUnit(unit, game));
			    		
			    	}
	    		}
		    	//change race away from random
		    	if(unit.getPlayer().equals(game.enemy()) && game.enemy().getRace() == Race.Unknown) {
		    		enemyRace = unit.getType().getRace();
		    	}
	    	}
    	} catch (Exception e) {
			try {
				writer = new BufferedWriter(new FileWriter("bwapi-data/write/exceptions.txt", true));
				writer.append(e.getMessage() + "\r\n");
				writer.close();
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}			
    		e.printStackTrace(System.out);
    	}
    }
    
    @Override
    public void onUnitDestroy(Unit unit) { 
    	try {
    		//update building info
        	if(enemyBuildings.containsKey(unit.getID())) {    		
        		enemyBuildings.remove(unit.getID());
        	}
        	if(enemyArmy.containsKey(unit.getID())) {    		
        		enemyArmy.remove(unit.getID());
        	}
        	
        	//prevent weird stuff from happening if a drone turns into an assimilator
        	if(unit.getType() != UnitType.Protoss_Scarab && unit.getType() != UnitType.Zerg_Larva) {
	        	if(unit.getPlayer() == self) {
	        		if(!unitLostTab.containsKey(unit.getType()))
	        			unitLostTab.put(unit.getType(), 1);
	        		else
	        			unitLostTab.put(unit.getType(), unitLostTab.get(unit.getType())+1);
	        		
	        		enemyArmySupplyKilled += unit.getType().supplyRequired();
	        	} else if(unit.getPlayer() == game.enemy()) {
	        		if(!enemiesKilledTab.containsKey(unit.getType()))
	        			enemiesKilledTab.put(unit.getType(), 1);
	        		else
	        			enemiesKilledTab.put(unit.getType(), enemiesKilledTab.get(unit.getType())+1);
	        		
	        		armySupplyKilled += unit.getType().supplyRequired();
	        	}
        	}
        	
        	//update list of MyUnits
        	if(unit.getPlayer() == self) {
        		if(myUnits.containsKey(unit.getID()))
        			myUnits.remove(unit.getID());
        	}
        	
    		//crown a new bisu probe scout
	    	if(game.enemy().getUpgradeLevel(UpgradeType.Ion_Thrusters) == 0 && game.enemy().getUpgradeLevel(UpgradeType.Metabolic_Boost) == 0
	    		&& scoutingProbe != null && unit.getID() == scoutingProbe.getID()) {
	    		Unit p = null;
	    		for(Unit u: self.getUnits()) {
	    			if(u.getType() == UnitType.Protoss_Probe && !u.isCarryingGas() && !u.isCarryingMinerals()
	    				&& (p == null 
	    				|| u.getPosition().getApproxDistance(unit.getPosition()) < p.getPosition().getApproxDistance(unit.getPosition()))) {
	    				
	    				p = u;
	    			}
	    		}
	    		scoutingProbe = p;
	    		if(p != null && myUnits.containsKey(p.getID())) {
	    			MyUnit probe = myUnits.get(p.getID());
	    			if(probe.getSquad() != null)
	    				probe.getSquad().removeUnit(probe);
	    		}
	    	}
    	} catch (Exception e) {
			try {
				writer = new BufferedWriter(new FileWriter("bwapi-data/write/exceptions.txt", true));
				writer.append(e.getMessage() + "\r\n");
				writer.close();
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}			
    		e.printStackTrace(System.out);
    	}
    }
    
    @Override
    public void onUnitMorph(Unit unit) {
    	if((unit.getType().isBuilding() || unit.getType() == UnitType.Terran_Siege_Tank_Siege_Mode) && unit.getPlayer() == game.enemy()) {    	
    		enemyBuildings.put(unit.getID(), new HisUnit(unit, game));
    	}
    	if(unit.getType() == UnitType.Terran_Siege_Tank_Tank_Mode && unit.getPlayer() == game.enemy()) {    	
    		enemyBuildings.remove(unit.getID());
    	}
    	if(unit.getType() == UnitType.Zerg_Drone) {
    		enemyArmy.remove(unit.getID());
    	}
    	
    	
    	//archon morphing
    	if(unit.getPlayer() == self) {
    		if(unit.getType() == UnitType.Protoss_Archon) {
				if(myUnits.containsKey(unit.getID())) {
					MyUnit templar = myUnits.get(unit.getID());
					MyUnit archon = new Archon(unit, game);
					templar.getSquad().add(archon);
					templar.getSquad().removeUnit(templar);
					myUnits.remove(unit.getID());
					myUnits.put(unit.getID(), archon);
				}
    		}
    	}
    	//prevent weird stuff from happening when drone makes an extractor
		if(unit.getType() == UnitType.Zerg_Extractor) {
			if(enemiesKilledTab.containsKey(UnitType.Zerg_Drone)) {
				if(enemiesKilledTab.get(UnitType.Zerg_Drone) == 1)
					enemiesKilledTab.remove(UnitType.Zerg_Drone);
				else
					enemiesKilledTab.put(unit.getType(), enemiesKilledTab.get(unit.getType())-1);
			}
		}
    }
    
    @Override
    public void onUnitCreate(Unit unit) {
    	try {
    		if(unit.getPlayer() == self) {
		        //System.out.println("New unit discovered " + unit.getType());
		    	//Advance the BO if a unit is created
		    	if (!unit.getType().isBuilding() && !unit.getType().isWorker()) {
		        	int i = 0;
		        	boolean match = false;
		        	while (i<selectedBO.length && !match) {
		        		if(!BOChecklist[i] && selectedBO[i] == unit.getType()) {
		        			BOChecklist[i] = true;
		        			match = true;
		        		}
		        		i++;
		        	}
		        	BOProgress = Math.max(0,i-1);
		        } else if (unit.getType() == UnitType.Protoss_Pylon && BOProgress == 1) {
		        	//initiate scouting
		    		Unit p = null;
		    		for(Unit u: self.getUnits()) {
		    			if(u.getType() == UnitType.Protoss_Probe && (p == null 
			    			|| u.getPosition().getApproxDistance(unit.getPosition()) < p.getPosition().getApproxDistance(unit.getPosition()))) {
		    				p = u;
		    			}
		    		}
		    		scoutingProbe = p;
		    		if(myUnits.containsKey(p.getID())) {
		    			MyUnit probe = myUnits.get(p.getID());
		    			if(probe.getSquad() != null)
		    				probe.getSquad().removeUnit(probe);
		    		}
		    	}
		        if(unit.getType() == selectedBO[selectedBO.length-1] && BOProgress >= selectedBO.length-1) {
		        	game.sendText("Build completed in: " + game.elapsedTime() + " seconds.");
		        }
    		}
		} catch (Exception e) {
			try {
				writer = new BufferedWriter(new FileWriter("bwapi-data/write/exceptions.txt", true));
				writer.append(e.getMessage() + "\r\n");
				writer.close();
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			e.printStackTrace(System.out);
		}
    }
        
    @Override
    public void onUnitComplete(Unit unit) {   
    	try {
	    	if(game.getFrameCount() > 0) {
		    	if(unit.getPlayer() == self) {
		    		if(unit.getType() == UnitType.Protoss_Zealot) {
		    			Zealot u = new Zealot(unit, game);
		    	   		freeAgents.add(u);
		        		myUnits.put(unit.getID(), u);
		    		} else if(unit.getType() == UnitType.Protoss_Dragoon) {
		    			Dragoon u = new Dragoon(unit, game);
		    	   		freeAgents.add(u);
		        		myUnits.put(unit.getID(), u);
		    		} else if(unit.getType() == UnitType.Protoss_High_Templar) {
		    			HighTemplar u = new HighTemplar(unit, game);
		    	   		freeAgents.add(u);
		        		myUnits.put(unit.getID(), u);
		    		} else if(unit.getType() == UnitType.Protoss_Probe) {
		    			Probe u = new Probe(unit, game);
		    	   		newWorkers.add(u);
		        		myUnits.put(unit.getID(), u);
		    		} else if(unit.getType() == UnitType.Protoss_Shuttle) {
		    			Shuttle u = new Shuttle(unit, game);
		        		myUnits.put(unit.getID(), u);
		    		} else if(unit.getType() == UnitType.Protoss_Reaver) {
		    			Reaver u = new Reaver(unit, game);
		        		myUnits.put(unit.getID(), u);
		    		}
		    	}
	    	}
		} catch (Exception e) {
			try {
				writer = new BufferedWriter(new FileWriter("bwapi-data/write/exceptions.txt", true));
				writer.append(e.getMessage() + "\r\n");
				writer.close();
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			e.printStackTrace(System.out);
		}
    }
    
    @Override
    public void onStart() {    	
        game = mirror.getGame();
        self = game.self();
   
        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");
        
        game.setCommandOptimizationLevel(3);
        
        //race needs its own variable to let it change away from random
        enemyRace = game.enemy().getRace();
        try {
			writer = new BufferedWriter(new FileWriter("bwapi-data/write/exceptions.txt", true));
			writer.append("vs " + game.enemy().getName() + " (" + enemyRace + ") on" + game.mapFileName() + "\r\n");
			writer.close();
		} catch (Exception e) {
			// TODO Auto-generated catch blocks
			e.printStackTrace();
		} 
        
        //initialize everything
        enemyBuildingTab = new HashMap<UnitType,Integer>();
        enemyCompletedBuildingTab = new HashMap<UnitType, Integer>();
        enemyArmyTab = new HashMap<UnitType, Integer>();
        enemyArmy = new HashMap<Integer, HisUnit>();
        enemiesKilledTab = new HashMap<UnitType, Integer>();
        unitLostTab = new HashMap<UnitType, Integer>();
        enemyBuildings = new HashMap<Integer, HisUnit>();
        enemyResearchTab = new HashSet<TechType>();
        myUnits = new HashMap<Integer, MyUnit>();
        freeAgents = new Squad(game);
        mainArmy = new Squad(game);
        newWorkers = new Squad(game);
        army = new ArrayList<Squad>();
        bases = new HashMap<Region, MyBase>();
        enemyBases = new HashMap<Region, HisBase>();
        gasBases = 1;
        armySupplyKilled = 0;
        enemyArmySupplyKilled = 0;
        armySupply = 0;
        enemyArmySupply = 0;
        for(Unit u: self.getUnits()) {
        	if(u.getType() == UnitType.Protoss_Probe) {
        		Probe p = new Probe(u, game);
        		newWorkers.add(p);
        		myUnits.put(u.getID(), p);
        	}
        }
        
        nextItem = null;
        nextItemX = 0;
        nextItemY = 0;
        //used for filling up space in late game with pylons
        pylonSkip = 0;
        
        saveFor = null;
        saveForIndex = 0;
        BOProgress = 0;
        pullGas = 0;
        resumeGas = 0;
        gasCap = 3;
        buildDone = false;
        
        
        scoutingProbe = null;
        enemyMain = null;
        
        mainChoke = null;
        naturalChoke = null;
        lateGameChoke = null;
        natural = null;
        //define chokepoints
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	if(!baseLocation.getPosition().equals(BWTA.getStartLocation(self).getPosition())
        		&& !baseLocation.isIsland() && !baseLocation.isMineralOnly() && (natural == null
        		|| baseLocation.getGroundDistance(BWTA.getStartLocation(self)) < natural.getGroundDistance(BWTA.getStartLocation(self)))) {
        		natural = baseLocation;
        	}
        }

        Region mainRegion = BWTA.getRegion(self.getStartLocation());
        for(Chokepoint choke: natural.getRegion().getChokepoints()) {
        	if(choke.getRegions().first.equals(mainRegion)
        		|| choke.getRegions().second.equals(mainRegion)) {
        		mainChoke = choke;
        	}
        }
        if(mainChoke == null) {
        	//Andromeda only pretty much
            for(Chokepoint choke: natural.getRegion().getChokepoints()) {
            	if(mainChoke == null || choke.getWidth() < mainChoke.getWidth()) {
            		mainChoke = choke;
            	}
            }
        }
        for(Chokepoint choke: natural.getRegion().getChokepoints()) {
        	if(!choke.equals(mainChoke) && (naturalChoke == null || choke.getWidth() > naturalChoke.getWidth())) {
        		naturalChoke = choke;
        	}
        }
  	   	
//        for(Region r: BWTA.getRegions()) {
//        	if(r.getChokepoints().contains(naturalChoke) && !r.getBaseLocations().contains(natural)) {
//                for(Chokepoint choke: r.getChokepoints()) {
//                	if(choke.getCenter().getApproxDistance(BWTA.getStartLocation(self).getPosition()) < 30*32 && choke.getWidth() > 3*32
//                		&& (lateGameChoke == null || 
//                		choke.getCenter().getApproxDistance(BWTA.getStartLocation(self).getPosition())
//                		> lateGameChoke.getCenter().getApproxDistance(BWTA.getStartLocation(self).getPosition()))) {
//                		lateGameChoke = choke;
//                	}
//                }
//        	}
//        }
        
        //player control enable
        game.enableFlag(1);
        game.setLocalSpeed(1);
        
        selectedBO = pickBuild(enemyRace);
        
        BOChecklist = new boolean[selectedBO.length];
        
        //nextItemX = self.getStartLocation().getX();
        //nextItemY = self.getStartLocation().getY() + 4;
        
        //initialize potential maps
        visionMap = new double[game.mapWidth()][game.mapHeight()];
        drawMaps();
    }
    
    public UnitType[] pickBuild(Race r) {
        UnitType[] chosenBuild;
    	if (r == Race.Terran) {
    		chosenBuild = new UnitType[]{UnitType.Protoss_Nexus,
    				UnitType.Protoss_Pylon, 
    				UnitType.Protoss_Gateway, 
    				UnitType.Protoss_Assimilator, 
    				UnitType.Protoss_Cybernetics_Core,
    				UnitType.Protoss_Pylon,
    				UnitType.Protoss_Dragoon,
    				UnitType.Protoss_Nexus,
    				UnitType.Protoss_Gateway,
    				UnitType.Protoss_Dragoon,
    				UnitType.Protoss_Pylon,
    				UnitType.Protoss_Dragoon,
    				UnitType.Protoss_Dragoon,
    				UnitType.Protoss_Robotics_Facility};
            
            saveForIndex = 7;
            pullGas = 6;
            resumeGas = 10;
    	} else if (r == Race.Zerg && naturalChoke != null)  {
    		chosenBuild = new UnitType[]{UnitType.Protoss_Nexus,
    				UnitType.Protoss_Pylon,     
    				UnitType.Protoss_Forge,
    				UnitType.Protoss_Photon_Cannon, 
    				UnitType.Protoss_Photon_Cannon,
    				UnitType.Protoss_Nexus,
    				UnitType.Protoss_Pylon,
    				UnitType.Protoss_Gateway,
    				UnitType.Protoss_Assimilator,
    				UnitType.Protoss_Pylon, 
    				UnitType.Protoss_Zealot,    
    				UnitType.Protoss_Cybernetics_Core, 
    				UnitType.Protoss_Zealot,    
    				UnitType.Protoss_Stargate,
    				UnitType.Protoss_Zealot,
    				UnitType.Protoss_Robotics_Facility};    				
            
            saveForIndex = 0;
            pullGas = 0;
            resumeGas = 0;
    	} else if (r == Race.Protoss) {
    		chosenBuild = new UnitType[]{UnitType.Protoss_Nexus,
    				UnitType.Protoss_Pylon,     
    				UnitType.Protoss_Gateway,
    				UnitType.Protoss_Assimilator, 
    				UnitType.Protoss_Zealot,
    				UnitType.Protoss_Pylon,  
    				UnitType.Protoss_Cybernetics_Core,
    				UnitType.Protoss_Zealot,
    				UnitType.Protoss_Dragoon,
    				UnitType.Protoss_Pylon,
    				UnitType.Protoss_Robotics_Facility,
    				UnitType.Protoss_Dragoon,
    				UnitType.Protoss_Gateway};
            
            saveForIndex = 0;
            pullGas = 0;
            resumeGas = 0;
    	} else { //random or 1 base vs. zerg
    		chosenBuild = new UnitType[] {UnitType.Protoss_Nexus,
    				UnitType.Protoss_Pylon,     
    				UnitType.Protoss_Gateway,
    				UnitType.Protoss_Pylon,  
    				UnitType.Protoss_Zealot,
    				UnitType.Protoss_Assimilator, 
    				UnitType.Protoss_Zealot,    
    				UnitType.Protoss_Cybernetics_Core};
            
            saveForIndex = 0;
            pullGas = 0;
            resumeGas = 0;
    	}
    	return chosenBuild;
    }

    public void drawMaps() {
    	walkMap = new double[game.mapWidth()][game.mapHeight()];
    	for(int x=0; x<game.mapWidth(); x++) {
	    	for(int y=0; y<game.mapHeight(); y++) {
	    		walkMap[x][y] = 999;
	    	}	    	
	    }
    	for(int x=0; x<game.mapWidth(); x++) {
	    	for(int y=0; y<game.mapHeight(); y++) {
	    		if(y>0) {
	    			if(!walkable(x,y-1) && walkable(x,y)) {	    		
		    			walkMap[x][y-1] = 1;
		    			walkMap[x][y] = 0;
	    			} else if(walkable(x,y-1) && !walkable(x,y)) {
	    				walkMap[x][y-1] = 0;
		    			walkMap[x][y] = 1;
	    			} else if(walkMap[x][y-1] <999 && walkable(x,y-1) && walkable(x,y)) {
		    			walkMap[x][y] = walkMap[x][y-1]-1;
	    			} else if(walkMap[x][y-1] <999 && !walkable(x,y-1) && !walkable(x,y)) {
		    			walkMap[x][y] = walkMap[x][y-1]+1;
	    			}
	    		}
	    	}
	    } 	
    	for(int x=game.mapWidth()-1; x>=0; x--) {
	    	for(int y=game.mapHeight()-1; y>=0; y--) {
	    		if(y<game.mapHeight()-1) {
	    			if(walkMap[x][y] < 999) {
		    			if(walkMap[x][y] > walkMap[x][y+1]+1) {	
			    			walkMap[x][y] = walkMap[x][y+1]+1;
		    			} else if(walkMap[x][y] < walkMap[x][y+1]-1) {
		    				walkMap[x][y] = walkMap[x][y+1]-1;
		    			}
	    			} else {
	    				if(walkMap[x][y+1] <1) {
	    					walkMap[x][y] = walkMap[x][y+1]-1;	    					
	    				} else {
	    					walkMap[x][y] = walkMap[x][y+1]+1;
	    				}
	    			}
	    		}
	    	}
    	}
    	for(int x=0; x<game.mapWidth(); x++) {
	    	for(int y=0; y<game.mapHeight(); y++) {
	    		if(x>0) {
	    			if(!walkable(x-1,y) && walkable(x,y)) {	    		
		    			walkMap[x-1][y] = 1;
		    			walkMap[x][y] = 0;
	    			} else if(walkable(x-1,y) && !walkable(x,y)) {
	    				walkMap[x-1][y] = 0;
		    			walkMap[x][y] = 1;
	    			}
	    		}
	    	}
	    }
    	for(int x=0; x<game.mapWidth(); x++) {
	    	for(int y=0; y<game.mapHeight(); y++) {
	    		if(x>0 && walkMap[x][y] != 0 && walkMap[x][y] != 1) {
	    			if(walkMap[x][y] > walkMap[x-1][y]+1) {
		    			walkMap[x][y] = walkMap[x-1][y]+1;
	    			} else if(walkMap[x][y] < walkMap[x-1][y]-1) {
		    			walkMap[x][y] = walkMap[x-1][y]-1;
	    			}
	    		}
	    	}
	    }   
    	for(int x=game.mapWidth()-1; x>=0; x--) {
	    	for(int y=game.mapHeight()-1; y>=0; y--) {		    	
	    		if(x<game.mapWidth()-1 && walkMap[x][y] != 0 && walkMap[x][y] != 1) {
	    			if(walkMap[x][y] > walkMap[x+1][y]+1) {
		    			walkMap[x][y] = walkMap[x+1][y]+1;
	    			} else if(walkMap[x][y] < walkMap[x+1][y]-1) {
		    			walkMap[x][y] = walkMap[x+1][y]-1;
	    			}
	    		}
	    	}
    	}	    
    }
    
    public boolean walkable(int x, int y) {
    	boolean walkable = true;
    	for(int dx=0;dx<4;dx++) {
    		for(int dy=0;dy<4;dy++) {
    			walkable &= game.isWalkable(x*4+dx, y*4+dy);
    		}
    	}
    	return walkable;
    }
    
    @Override
    public void onFrame() {
    	try {
    		long frameLength = System.currentTimeMillis();
	        //game.setTextSize(10);
	        
	        //Clear BO completion.
	        //when a unit is built, it stays complete on the BO even if it dies
	        for (int i=0; i<BOChecklist.length; i++) {
	        	if(selectedBO[i].isBuilding()) {
	        		BOChecklist[i] = false;
	        	}
	        }
	        reservedMinerals = 0;
	        reservedGas = 0;
	        supplyPerFrame = 0;
	        
	        //Usually used to save up for expansion
	        if (saveForIndex > 0 && BOProgress == saveForIndex && BOProgress > 0) {
	        	saveFor = selectedBO[saveForIndex];
	        	reservedMinerals += saveFor.mineralPrice();
	        	reservedGas += saveFor.gasPrice();        	
	        }
	    		
	        long tic = System.currentTimeMillis();
	        countUnits();    
	        game.drawTextScreen(500, 60, (System.currentTimeMillis()-tic) + " ms counting units");
	        //update BO progress
	        int i = 0;
	        boolean keepChecking = true;
	        while (i<BOChecklist.length && keepChecking) {
	        	if(!BOChecklist[i]) {
	        		nextItem = selectedBO[i];
	        		BOProgress = i;
	        		keepChecking = false;
	        	}
	        	i++;
	        }
	        
	        //true if initial BO is complete
	        if(keepChecking) {
	        	BOProgress = i;
	        	nextItem = null;
	        	buildDone = true;
	        }
	        
	        //for pulling workers out of gas
	        if(BOProgress > 0 && BOProgress >= pullGas && BOProgress < resumeGas) {
	        	gasCap = 2;
	        } else {
	        	gasCap = 3;
	        }
	        
	        tic = System.currentTimeMillis();
	        trainAndResearch();    
	        game.drawTextScreen(500, 75, (System.currentTimeMillis()-tic) + " ms training/researching");
	        //true if initial BO is complete
	        if(keepChecking) {
	        	if(enemyRace == Race.Terran)
	        		nextItem = transitionT();
	        	else if (enemyRace == Race.Zerg)
	        		nextItem = transitionZ();
	        	else
	        		nextItem = transitionP();
	        }
	        
	        //determine defensive chokepoint
    		if(lateGameChoke != null && (self.allUnitCount(UnitType.Protoss_Nexus) > 2
        		|| (self.allUnitCount(UnitType.Protoss_Nexus) == 2 && nextItem == UnitType.Protoss_Nexus))) {
        			
    			defenseChoke = lateGameChoke;    			
    		} else if(naturalChoke != null && (self.allUnitCount(UnitType.Protoss_Nexus) >= 2
            	|| (self.allUnitCount(UnitType.Protoss_Nexus) == 1 && nextItem == UnitType.Protoss_Nexus))) {
    			defenseChoke = naturalChoke;
    		} else if(mainChoke != null) {
    			defenseChoke = mainChoke;
    		}
    		if(defenseChoke != null)
    			game.drawLineMap(defenseChoke.getSides().first, defenseChoke.getSides().second, Color.Red);
	        
    		//mining
	        tic = System.currentTimeMillis();
	        gather();  	        
	        game.drawTextScreen(500, 90, (System.currentTimeMillis()-tic) + " ms gathering");
	        
	        if(scoutingProbe != null) {
	        	scout(scoutingProbe);
	        	game.drawTextMap(scoutingProbe.getPosition(), "Bisu Probe");
	        }
	        tic = System.currentTimeMillis();
	     	    	
	        //army control
	        controlArmy();
	        game.drawTextScreen(500, 105, (System.currentTimeMillis()-tic) + " ms controlling army");
	        
	        if(System.currentTimeMillis()-frameLength < 74) { //if you're going to get booted for slowness, skip building instead
		        if (nextItem != null && nextItem.isBuilding()) {
		        	tic = System.currentTimeMillis();
		            updateBuildingMap(nextItem);
		        	buildBuilding(nextItem, nextItemX, nextItemY);  
		        	game.drawTextScreen(500, 120, (System.currentTimeMillis()-tic) + " ms building");
		        } else if(BOProgress < selectedBO.length-1 && saveForIndex == BOProgress+1) {
		        	//usually for sending probe out early for expo
		        	tic = System.currentTimeMillis();
		        	updateBuildingMap(selectedBO[BOProgress + 1]);
		        	buildBuilding(selectedBO[BOProgress + 1], nextItemX, nextItemY);  
		        	game.drawTextScreen(500, 120, (System.currentTimeMillis()-tic) + " ms building");
		        }
	        }
	        game.drawTextScreen(500, 35, (game.getAPM() + " APM"));
	        
	        BOProgress = Math.max(0, i-1);
	        if(nextItem != null)
	        	game.drawTextScreen(250, 30, "Next Item: " + BOProgress + " " + nextItem.toString());
	        else
	        	game.drawTextScreen(250, 30, "Next Item: -----");
	        
	        frameLength = System.currentTimeMillis()-frameLength;
	        if(frameLength >= 85) {
		        writer = new BufferedWriter(new FileWriter("bwapi-data/write/exceptions.txt", true));
		        writer.append(""+frameLength+ "\r\n");
		        writer.close();
	        }
    	} catch (Exception e) {
    		e.printStackTrace(System.out);
    		try {
    			writer = new BufferedWriter(new FileWriter("bwapi-data/write/exceptions.txt", true));
    			writer.append(e.getMessage() + "\r\n");
    			writer.close();
    		} catch(Exception IOE) {
    			IOE.printStackTrace(System.out);
    		}
    	}
    }    
    /** Keeps track of your own units, income, and BO completion progress
     * 
     */
    public void countUnits() throws Exception {
        StringBuilder units = new StringBuilder("My units:\n");
        //reset counters
        mineralsPerFrame = 0;
        gasPerFrame = 0;
        
        //vision map time attenuation
        for(int x=0; x<game.mapWidth(); x++) {
        	for(int y=0; y<game.mapHeight(); y++) {
        		if(game.isVisible(x, y)) {
        			visionMap[x][y] = 0;
        		} else {
        			visionMap[x][y] ++;
        		}
//        		game.drawTextMap(x*32,y*32,""+walkMap[x][y]);
        	}
        }
        
        //reset unit lists for bases
        Iterator<Region> itr = bases.keySet().iterator();
        Region r = null;
        while(itr.hasNext()) {
        	r = itr.next();
        	bases.get(r).clear();
        }
        
        //same for enemy bases
        for(Region region: enemyBases.keySet()) {
        	HisBase base = enemyBases.get(region);
        	base.clearTurrets();
    	}
        
        for(int ID: myUnits.keySet()) {
        	myUnits.get(ID).setCommandGiven(false);
//        	game.drawTextMap(myUnits.get(ID).getPosition(), ""+myUnits.get(ID).getSquad());
        }
        
        gasBases = 0;
        armySupply = 0;
        //iterate through my units
        for (Unit myUnit : self.getUnits()) {   
        	//cancel a dying building
        	if(myUnit.getType().isBuilding()) {
        		if(myUnit.isUnderAttack() && myUnit.getHitPoints() < 20) {
        			if(!myUnit.isCompleted()) {
        				myUnit.cancelConstruction();
        			} else if(myUnit.isTraining()) {
        				myUnit.cancelTrain();
        			} else if(myUnit.isResearching()) {
        				myUnit.cancelResearch();
        			} else if(myUnit.isUpgrading()) {
        				myUnit.cancelUpgrade();
        			}
        		} 
        	}
        	
        	if(myUnit.getType() == UnitType.Protoss_Nexus) {
    			r = BWTA.getRegion(myUnit.getPosition());
    			MyBase newBase = new MyBase(game, myUnit);
    			if(!bases.containsKey(r)) {
    				bases.put(r, newBase);
    			}
        	}
        	//assign buildings to bases
    		if(myUnit.getType().isBuilding()) {
    			r = BWTA.getRegion(myUnit.getPosition());
    			if(bases.containsKey(r))
    				bases.get(r).addBuilding(myUnit);
    		}
        	
            units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");
            if (myUnit.getType().isWorker()) {
            	if (myUnit.isGatheringMinerals()) {
            		mineralsPerFrame += 0.0474;   
            	} else if (myUnit.isGatheringGas()) {
            		gasPerFrame += 0.072;
            	}
//            	if(myUnit.getTarget() !=null) {
//            		game.drawTextMap(myUnit.getPosition(), ""+Math.sqrt(Math.pow(myUnit.getVelocityX(),2) + Math.pow(myUnit.getVelocityY(),2)));
//            	}
            } else if(myUnit.getType().isBuilding()) {
            	boolean match = false;
            	int i = 0;
            	while(!match && i<BOChecklist.length) {
            		if (selectedBO[i] == myUnit.getType() && !BOChecklist[i]) {
            			BOChecklist[i] = true;  
            			match = true;
            		}
            		i++;
            	}
            } else {
            	armySupply += myUnit.getType().supplyRequired();
//            	if(myUnit.getTarget() ==null) {
//            		game.drawTextMap(myUnit.getPosition(), ""+Math.sqrt(Math.pow(myUnit.getVelocityX(),2) + Math.pow(myUnit.getVelocityY(),2)));
//            	}
            }
        }
        for (Unit neutralUnit : game.neutral().getUnits()) {
    		
        	if(enemyBuildings.containsKey(neutralUnit.getID())) {        		
        		enemyBuildings.get(neutralUnit.getID()).update();
        		//in case someone does extractor trick or refinery is killed
        		if(neutralUnit.isVisible())
        			enemyBuildings.remove(neutralUnit.getID());
        	}
        	
        	int saturation = 0;
            if (neutralUnit.getType().isMineralField()) {    
            	for (Unit u: game.getUnitsInRadius(neutralUnit.getPosition(), 6*PIXELS_PER_TILE)) {
            		if(u.getPlayer().equals(self)) {
	            		if(u.getType() == UnitType.Protoss_Probe) {
	            			if(u.getTarget() != null && u.getTarget().getID() == neutralUnit.getID()) {	         
	            				saturation++;
	            			}	 
	            		}
            		}
            	}
            	game.drawTextMap(neutralUnit.getPosition(), saturation+"");
            }
            
        	//assign resources to bases
    		if(neutralUnit.getType().isMineralField()) {// || neutralUnit.getType() == UnitType.Resource_Vespene_Geyser) {
    			r = BWTA.getRegion(neutralUnit.getPosition());
    			if(bases.containsKey(r)) {
    				bases.get(r).putResource(neutralUnit, saturation);
    			}
    		}
        }
        
        //delete bases that are empty
        itr = bases.keySet().iterator();
        r = null;
        patchCount = 0;
        while(itr.hasNext()) {
        	r = itr.next();
        	if(bases.get(r).getAllBuildingCount() == 0)
        		itr.remove();
        	else {
    			if(!bases.get(r).getBaseLocation().isMineralOnly() && bases.get(r).isCompleted()) {
    				gasBases++;
    			}
        		patchCount += bases.get(r).getPatchCount();
        	}
        }
        //reduce income level to account for saturation
        mineralsPerFrame = Math.min(mineralsPerFrame, patchCount*3*0.0474f);
//        String BODisplay = "";
//        for(int i=0; i<selectedBO.length; i++) {
//        	BODisplay += selectedBO[i].toString() + ": " + BOChecklist[i] + "\n";
//        }
//        game.drawTextScreen(10, 30, BODisplay);
                
        //game.drawTextScreen(250, 15, "Minerals Per Frame: " + mineralsPerFrame);
        //draw my units on screen
        attackPosition = null;
        airAttackPosition = null;
        //now for enemy units
        for (Unit hisUnit: game.enemy().getUnits()) {
        	//check for intruders in my bases
        	if((!hisUnit.getType().groundWeapon().equals(WeaponType.None) || hisUnit.getType().isSpellcaster()
    	    	|| hisUnit.getType() == UnitType.Terran_Dropship || hisUnit.getType() == UnitType.Protoss_Shuttle)
        		&& (!hisUnit.getType().isWorker() || hisUnit.isAttacking()) && bases.containsKey(BWTA.getRegion(hisUnit.getPosition()))) {
        		bases.get(BWTA.getRegion(hisUnit.getPosition())).setUnderAttack(true);
        	}
        	
        	if(enemyBuildings.containsKey(hisUnit.getID())) {        		
        		enemyBuildings.get(hisUnit.getID()).update();
        		//in case someone does extractor trick or refinery is killed
        		if(hisUnit.isVisible() && hisUnit.getType() == UnitType.Resource_Vespene_Geyser)
        			enemyBuildings.remove(hisUnit.getID());
        	}
        	if(hisUnit.isVisible()) {
	        	//for some reason, the game remembers enemy positions from past games, so gotta check if explored
	        	if (enemyMain == null && game.isExplored(hisUnit.getTilePosition()) && hisUnit.getType().isResourceDepot()) {
	        		for(TilePosition t: game.getStartLocations()) {
	        			if(t.equals(hisUnit.getTilePosition())) {
	        				enemyMain = hisUnit.getTilePosition();       
	        				enemyBases.put(BWTA.getRegion(t), new HisBase(game, BWTA.getNearestBaseLocation(t)));
	        			}
	        		}
	        	}
	        	if (enemyMain == null && hisUnit.getType().isBuilding()) {
	        		enemyMain = hisUnit.getTilePosition();
	        	}
	        	if(hisUnit.isFlying() && (airAttackPosition == null || safety(hisUnit.getPosition())
	        		> safety(airAttackPosition))) {
	        		
	        		airAttackPosition = hisUnit.getPosition();
	        	}
	        	if(hisUnit.getType() != UnitType.Zerg_Larva && hisUnit.getType() != UnitType.Zerg_Egg
	        		&& hisUnit.getType() != UnitType.Resource_Vespene_Geyser && !hisUnit.isFlying()
		        	&& (!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)
		        	&& (attackPosition == null || safety(hisUnit.getPosition()) > safety(attackPosition))) {
	        		
	        		attackPosition = hisUnit.getPosition();
	        	}
        	}
        }
        
        //enemy buildings
        enemyBuildingTab.clear();
        enemyCompletedBuildingTab.clear();
        HisUnit u;
        for(int i: enemyBuildings.keySet()) {
        	u = enemyBuildings.get(i);
        	if(u.isCompleted()) {
//        		game.drawBoxMap(u.getX()-u.getType().width()/2, u.getY()-u.getType().height()/2,
//        				u.getX()+u.getType().width()/2, u.getY()+u.getType().height()/2, Color.Orange);
        		//count completed buildings
        		if(enemyCompletedBuildingTab.containsKey(u.getType()))
        			enemyCompletedBuildingTab.put(u.getType(), enemyCompletedBuildingTab.get(u.getType())+1);
        		else
        			enemyCompletedBuildingTab.put(u.getType(), 1);
        		
        		//find bases and their static defenses
        		if(u.getPosition() != null) {
	        		if(!u.getUnit().isFlying() && u.getType().isResourceDepot()) {//(u.getType().isResourceDepot() || u.getType().isRefinery())) {
	        			//don't drop bases without workers
	        			r = BWTA.getRegion(u.getPosition());
	        			BaseLocation base = BWTA.getNearestBaseLocation(u.getPosition());
	        			if(!enemyBases.containsKey(r) && base.getRegion().equals(r)) {
	        				enemyBases.put(r, new HisBase(game, base));
	        			}

	        		} else if(!u.getType().isBuilding() || u.getType().canAttack() 
    					|| u.getType() == UnitType.Terran_Bunker) {
	        			
	        			r = BWTA.getRegion(u.getPosition());
	        			if(enemyBases.containsKey(r)) {
	        				enemyBases.get(r).addTurret(u);
	        			}
	        		}
        		}
        	} else {
//        		game.drawBoxMap(u.getX()-u.getType().width()/2, u.getY()-u.getType().height()/2,
//        				u.getX()+u.getType().width()/2, u.getY()+u.getType().height()/2, Color.Brown);
        		//construction progress bar
//        		game.drawBoxMap(u.getX()-u.getType().width()/2, u.getY()-u.getType().height()/2+1,
//        				u.getX()-u.getType().width()/2+(u.getType().width()*(u.getType().buildTime()-u.getRemainingBuildTime()))/u.getType().buildTime(), 
//        				u.getY()-u.getType().height()/2+4, Color.Brown);
        	}
        	//count all buildings
    		if(enemyBuildingTab.containsKey(u.getType()))
    			enemyBuildingTab.put(u.getType(), enemyBuildingTab.get(u.getType())+1);
    		else
    			enemyBuildingTab.put(u.getType(), 1);

    		if(u.getTech() != null) {
    			enemyResearchTab.add(u.getType().researchesWhat().get(0));	//will need to change
    		}
        }
        
        //enemy base dropping
        dropBase = null;
        itr = enemyBases.keySet().iterator();
        while(itr.hasNext()) {
        	HisBase base = enemyBases.get(itr.next());
        	TilePosition baseTile = base.getBaseLocation().getTilePosition();
    		if(game.isVisible(baseTile) && game.getUnitsOnTile(baseTile).size() == 0) {
    			itr.remove();
    		} else {
	        	game.drawCircleMap(base.getPosition(), 7*8, Color.Red);
	        	for(HisUnit unit: base.getTurrets()) {
	        		if(unit.getPosition() != null) {
	                	game.drawCircleMap(unit.getPosition(), 32, Color.Yellow);
	        		}
	        	}
    			int workerCount = 0;
    			int patchCount = 0;
    			boolean scouted = true;
        		int scoutRange = 5;
        		int revisitTime = 300;
	    		if(visionMap[baseTile.getX()+2][baseTile.getY()+1] > revisitTime
	    			|| visionMap[baseTile.getX()+2-scoutRange][baseTile.getY()+1] > revisitTime
	    			|| visionMap[baseTile.getX()+2+scoutRange][baseTile.getY()+1] > revisitTime
	    			|| visionMap[baseTile.getX()+2][baseTile.getY()+1-scoutRange] > revisitTime
	    			|| visionMap[baseTile.getX()+2][baseTile.getY()+1+scoutRange] > revisitTime) {
	    			
	    			scouted = false;
	    		}
				for(Unit hisUnit: game.getUnitsInRadius(base.getPosition(), 8*32)) {
					if(hisUnit.getPlayer() == game.enemy() && hisUnit.getType().isWorker() && hisUnit.isCompleted()) {
						workerCount ++;
					}
					if(hisUnit.getType().isMineralField()) {
						patchCount ++;
					}
				}
	        	if(base.getTurrets().size() < 3 
	        		&& !(scouted && (workerCount < 3 || patchCount < 1))
	        		&& (dropBase == null || safety(base.getPosition()) > safety(dropBase.getPosition()))) {
	        		
	        		dropBase = base;
	        	}
    		}
        }
        
        Iterator<Integer> itr2 = enemyArmy.keySet().iterator();
        while(itr2.hasNext()) {
        	u = enemyArmy.get(itr2.next());
    		if(u.getType() != null && u.getUnit().isVisible() && u.getType().isBuilding())
    			itr2.remove();
			else
				u.update();
        }
        enemyArmyTab.clear();
        enemyArmySupply = 0;
        for(int i: enemyArmy.keySet()) {
        	u = enemyArmy.get(i);
        	//count all units
    		if(enemyArmyTab.containsKey(u.getType()))
    			enemyArmyTab.put(u.getType(), enemyArmyTab.get(u.getType())+1);
    		else
    			enemyArmyTab.put(u.getType(), 1);
    		
    		if(u.getType() != null && !u.getType().isWorker()) {
    			enemyArmySupply += u.getType().supplyRequired();
//    			if(u.getPosition() != null)
//    				game.drawTextMap(u.getPosition(), "" + u.getType());
    		}
        }

        //left panel debug messages
        
        //enemy intel update
        String enemyInfo = "";
//		for(UnitType t: enemyBuildingTab.keySet()) 
//			enemyInfo += t + ": " + enemyBuildingTab.get(t) + "\n";
//		for(TechType t: enemyResearchTab) 
//			enemyInfo += t +"\n";
//		for(UnitType t: enemyArmyTab.keySet()) 
//			enemyInfo += t +": " + enemyArmyTab.get(t) + "\n";
//        for(UnitType t: unitLostTab.keySet()) 
//			enemyInfo += t +": " + unitLostTab.get(t) + "\n";
//        for(UnitType t: enemiesKilledTab.keySet()) 
//			enemyInfo += t +": " + enemiesKilledTab.get(t) + "\n";
        enemyInfo += armySupply+ " " + enemyArmySupply + "\n";
        enemyInfo += armySupplyKilled+ " " + enemyArmySupplyKilled + "\n";
        game.drawTextScreen(10, 30, enemyInfo);
        
        if(attackPosition == null) {
        	//if no units to attack, go for buildings
	        for(int i: enemyBuildings.keySet()) {
	        	u = enemyBuildings.get(i);
	        	if (u.getType() != UnitType.Resource_Vespene_Geyser 
	        		&& (u.getPosition() != null && (attackPosition == null || safety(u.getPosition()) 
	        		> safety(attackPosition)))) {
	        		
	        		attackPosition = u.getPosition();
	        	}
	        }  
//	        for(Unit hisUnit: game.enemy().getUnits()) {
//	        	if(hisUnit.exists() && hisUnit.getType() != UnitType.Zerg_Larva && hisUnit.getType() != UnitType.Zerg_Egg
//	        		&& (attackPosition == null || hisUnit.getPosition().getApproxDistance(self.getStartLocation().toPosition()) 
//	        		< attackPosition.getApproxDistance(self.getStartLocation().toPosition()))) {
//	        		
//	        		attackPosition = hisUnit.getPosition();
//	        	}
//	        }   
        }
    	if(attackPosition == null) {
    		//if no buildings to attack, go to default main
    		if(enemyMain != null) {
	    		attackPosition = enemyMain.toPosition();
//    		} else if(defenseChoke != null) {
//    			attackPosition = defenseChoke.getCenter();
    		} else {
    			attackPosition = self.getStartLocation().toPosition();
    		}
    	}
    	if(enemyMain != null)
    		game.drawTextMap(enemyMain.toPosition(), "I see you!");    	
    }
    
    /** Train units and research tech and upgrades. The buildings are sorted by priority order.
     * 
     */
    public void trainAndResearch() throws Exception {
        for (Unit myUnit : self.getUnits()) {
            //if there's enough minerals, train a probe
            if (myUnit.getType() == UnitType.Protoss_Nexus && myUnit.isCompleted()
            	&& self.completedUnitCount(UnitType.Protoss_Probe) <= patchCount * 3 + self.completedUnitCount(UnitType.Protoss_Assimilator) * 3
            	&& self.completedUnitCount(UnitType.Protoss_Probe) <= 80) {          	
     
            	if(self.completedUnitCount(UnitType.Protoss_Probe) < 4 && self.minerals() > 50 && !myUnit.isTraining()) {
            		//short circuit for when all probes are killed
            		myUnit.build(UnitType.Protoss_Probe);
            	} else {
            		prepareUnit(myUnit, UnitType.Protoss_Probe);
            	}
            }
        }        
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Cybernetics_Core) {
            	if (enemyRace == Race.Terran || self.allUnitCount(UnitType.Protoss_Dragoon) > 0) {
            		prepareUpgrade(myUnit, UpgradeType.Singularity_Charge);
            	} else if (self.completedUnitCount(UnitType.Protoss_Corsair) > 0
            		|| self.allUnitCount(UnitType.Protoss_Fleet_Beacon) > 0) {
            		
            		if(self.getUpgradeLevel(UpgradeType.Protoss_Air_Weapons) == 0) {
            			prepareUpgrade(myUnit, UpgradeType.Protoss_Air_Weapons);
            		} else if(self.getUpgradeLevel(UpgradeType.Protoss_Air_Weapons) <3) {
            			if(self.completedUnitCount(UnitType.Protoss_Fleet_Beacon) > 0) {
            				prepareUpgrade(myUnit, UpgradeType.Protoss_Air_Weapons);
            			}
            		} else if(self.getUpgradeLevel(UpgradeType.Protoss_Air_Armor) == 0) {
            			prepareUpgrade(myUnit, UpgradeType.Protoss_Air_Armor);
            		} else if(self.getUpgradeLevel(UpgradeType.Protoss_Air_Armor) <3
                		&& self.completedUnitCount(UnitType.Protoss_Fleet_Beacon) > 0) {
            			prepareUpgrade(myUnit, UpgradeType.Protoss_Air_Armor);
            		}
            	}
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Citadel_of_Adun) {
            	prepareUpgrade(myUnit, UpgradeType.Leg_Enhancements);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Templar_Archives) {
            	if(!self.hasResearched(TechType.Psionic_Storm))
            		prepareUpgrade(myUnit, TechType.Psionic_Storm);
            	else if(self.allUnitCount(UnitType.Protoss_High_Templar) >= 4)
            		prepareUpgrade(myUnit, UpgradeType.Khaydarin_Amulet);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Arbiter_Tribunal) {
            	if(!self.hasResearched(TechType.Stasis_Field))
            		prepareUpgrade(myUnit, TechType.Stasis_Field);
            	else if(!self.hasResearched(TechType.Recall))
            		prepareUpgrade(myUnit, TechType.Recall);
            	else
            		prepareUpgrade(myUnit, UpgradeType.Khaydarin_Core);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Forge && buildDone) {
            	if(self.getUpgradeLevel(UpgradeType.Protoss_Ground_Weapons) == 0)
            		prepareUpgrade(myUnit, UpgradeType.Protoss_Ground_Weapons);
            	else if(self.getUpgradeLevel(UpgradeType.Protoss_Ground_Weapons) <3) {
            		if(self.completedUnitCount(UnitType.Protoss_Templar_Archives) > 0) {  		
            			prepareUpgrade(myUnit, UpgradeType.Protoss_Ground_Weapons);
            		}
            	} else if(self.getUpgradeLevel(UpgradeType.Protoss_Ground_Armor) == 0) {
            		prepareUpgrade(myUnit, UpgradeType.Protoss_Ground_Armor);
            	} else if(self.getUpgradeLevel(UpgradeType.Protoss_Ground_Armor) <3) {
            		if(self.completedUnitCount(UnitType.Protoss_Templar_Archives) > 0) {
            			prepareUpgrade(myUnit, UpgradeType.Protoss_Ground_Armor);
            		}
            	} else if(self.getUpgradeLevel(UpgradeType.Protoss_Plasma_Shields) == 0) {
            		prepareUpgrade(myUnit, UpgradeType.Protoss_Plasma_Shields);
            	} else if(self.getUpgradeLevel(UpgradeType.Protoss_Plasma_Shields) <3
            		&& self.completedUnitCount(UnitType.Protoss_Cybernetics_Core) > 0) {
            		prepareUpgrade(myUnit, UpgradeType.Protoss_Plasma_Shields);
            	}
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Robotics_Support_Bay) {
            	if(self.allUnitCount(UnitType.Protoss_Reaver) > 0)
            		prepareUpgrade(myUnit, UpgradeType.Gravitic_Drive);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Observatory) {
            	if(self.allUnitCount(UnitType.Protoss_Observer) > 4)
            		prepareUpgrade(myUnit, UpgradeType.Gravitic_Boosters);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Stargate) {
            	if(!myUnit.isTraining()) {
            		if (BOProgress < selectedBO.length) {
            			if(selectedBO[BOProgress] == UnitType.Protoss_Corsair) {
            				prepareUnit(myUnit, UnitType.Protoss_Corsair);
            			}
            		} else if(enemyRace == Race.Zerg && (self.allUnitCount(UnitType.Protoss_Corsair) 
            			< 6 || (enemyArmyTab.containsKey(UnitType.Zerg_Mutalisk) && enemyArmyTab.get(UnitType.Zerg_Mutalisk) > 12)
            			|| (enemyArmyTab.containsKey(UnitType.Zerg_Scourge) && enemyArmyTab.get(UnitType.Zerg_Scourge) >= 10))) {
            			prepareUnit(myUnit, UnitType.Protoss_Corsair);
//            		} else if(enemyRace == Race.Terran) {
//            			prepareUnit(myUnit, UnitType.Protoss_Arbiter);
            		}
            	}  	
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Robotics_Facility && myUnit.isCompleted()) {
            	if(myUnit.isTraining()) {
            	} else if(self.allUnitCount(UnitType.Protoss_Shuttle) == 0) {
            		prepareUnit(myUnit, UnitType.Protoss_Shuttle);
            	} else if(self.allUnitCount(UnitType.Protoss_Observatory) > 0 && self.allUnitCount(UnitType.Protoss_Observer) == 0
            		&& enemyRace!= Race.Zerg) {
            		prepareUnit(myUnit, UnitType.Protoss_Observer);
            	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Support_Bay) > 0 && self.allUnitCount(UnitType.Protoss_Reaver) == 0) {
                	prepareUnit(myUnit, UnitType.Protoss_Reaver);
            	} else if(self.allUnitCount(UnitType.Protoss_Observatory) > 0 && self.completedUnitCount(UnitType.Protoss_Reaver) > 0 
            		&& self.allUnitCount(UnitType.Protoss_Observer) < 5) {
            		
            		prepareUnit(myUnit, UnitType.Protoss_Observer);
            	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Support_Bay) > 0 && self.allUnitCount(UnitType.Protoss_Shuttle) < 2) {
                	prepareUnit(myUnit, UnitType.Protoss_Shuttle);
            	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Support_Bay) > 0 && self.allUnitCount(UnitType.Protoss_Reaver) < 2) {
                	prepareUnit(myUnit, UnitType.Protoss_Reaver);
            	}
            }     
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Gateway) {
	            if(myUnit.isCompleted()) {
	            	if(BOProgress < selectedBO.length && !buildDone) {
	            		if(selectedBO[BOProgress] == UnitType.Protoss_Dragoon) {
	            			prepareUnit(myUnit, UnitType.Protoss_Dragoon);
	            		} else if(selectedBO[BOProgress] == UnitType.Protoss_Zealot) {
	            			prepareUnit(myUnit, UnitType.Protoss_Zealot);
	            		}
	            	} else if(self.minerals() > self.gas() + 700){
	            		prepareUnit(myUnit, UnitType.Protoss_Zealot);
	            	} else if(self.completedUnitCount(UnitType.Protoss_Templar_Archives) > 0 
	            		&& (self.allUnitCount(UnitType.Protoss_High_Templar) < 6 || self.gas() > self.minerals() + 700) 
	            		&& canAfford(UnitType.Protoss_High_Templar)) {
	            		
	            		prepareUnit(myUnit, UnitType.Protoss_High_Templar);
	            	} else if(((enemyRace != Race.Zerg
	            		&& !(self.completedUnitCount(UnitType.Protoss_Citadel_of_Adun) > 0 
	            		&& self.allUnitCount(UnitType.Protoss_Dragoon) > self.allUnitCount(UnitType.Protoss_Zealot))))
	            		|| ((enemyArmyTab.containsKey(UnitType.Zerg_Lurker) || enemyArmyTab.containsKey(UnitType.Zerg_Lurker_Egg))
	            		&& self.allUnitCount(UnitType.Protoss_Dragoon) < 0.6*self.allUnitCount(UnitType.Protoss_Zealot))) {
	            		//translation: if enemy is T or P and my citadel is finished, or if enemy has lurker tech, start making equal numbers
	            		//of goons and zlots
	            		
	            		prepareUnit(myUnit, UnitType.Protoss_Dragoon);
	            	} else {
	            		prepareUnit(myUnit, UnitType.Protoss_Zealot);
	            	}
            	} else {
            		supplyPerFrame += UnitType.Protoss_Zealot.supplyRequired()*1f/UnitType.Protoss_Zealot.buildTime();
            	}
            }     
        }
        game.drawTextScreen(250, 0, "Reserved Minerals: " + reservedMinerals);
        game.drawTextScreen(250, 15, "Reserved Gas: " + reservedGas);
    }
    
    /** Makes sure you always save enough money for key upgrades and upgrade if you can
     * 
     * @param Upgrader building
     * @param The upgrade or research you want
     */
    public void prepareUpgrade(Unit building, UpgradeType u) {
    	if(self.getUpgradeLevel(u) < self.getMaxUpgradeLevel(u)) {
	    	if (building.isCompleted()) {
	        	if (building.canUpgrade(u) && canAfford(u)) {
	            	building.upgrade(u);
	        	} else if(self.getUpgradeLevel(u) == 0 && !building.isUpgrading() && !building.isResearching()) {	         	
	        		reservedMinerals += u.mineralPrice();
	        		reservedGas += u.gasPrice();		            	
	        	}        	
	        } else {
	        	reservedMinerals += Math.max(0, u.mineralPrice()
	        		- building.getRemainingBuildTime() * mineralsPerFrame);
	    		reservedGas += Math.max(0, u.gasPrice()
	    			- building.getRemainingBuildTime() * gasPerFrame);
	        }
    	}
    }
    
    /** Makes sure you always save enough money for key upgrades and upgrade if you can
     * 
     * @param Upgrader building
     * @param The upgrade or research you want
     */
    public void prepareUpgrade(Unit building, TechType u) {
    	if(!self.hasResearched(u) && self.isResearchAvailable(u)) {
	    	if (building.isCompleted()) {
	        	if (building.canResearch(u) && canAfford(u)) {
	            	building.research(u);
	        	} else if(!self.hasResearched(u) && !building.isUpgrading() && !building.isResearching()) {	         	
	        		reservedMinerals += u.mineralPrice();
	        		reservedGas += u.gasPrice();		            	
	        	}        	
	        } else {
	        	reservedMinerals += Math.max(0, u.mineralPrice()
	        		- building.getRemainingBuildTime() * mineralsPerFrame);
	    		reservedGas += Math.max(0, u.gasPrice()
	    			- building.getRemainingBuildTime() * gasPerFrame);
	        }
    	}
    }
    
    /**Makes sure you always save enough money to continue production and produce if you can.
     * Also, update supply requirements.
     * @param building
     * @param myUnit
     */
    public void prepareUnit(Unit building, UnitType unit) {
    	if(building.getTrainingQueue().size() == 0) {
			if(building.isTraining()) {
				if(BOProgress >= selectedBO.length) {
		    		//make sure there will be enough money to build the next unit 		
		    		reservedMinerals += (int) Math.max(0, unit.mineralPrice() 
		    				- building.getRemainingTrainTime() * mineralsPerFrame);
		    		reservedGas += (int) Math.max(0, unit.gasPrice() 
		    				- building.getRemainingTrainTime() * gasPerFrame);
				}
			} else if(canAfford(unit) && building.canTrain(unit) && self.supplyUsed() + unit.supplyRequired() <= self.supplyTotal()) {
				building.train(unit);
			} else {
				if(BOProgress >= selectedBO.length) {
		    		reservedMinerals += unit.mineralPrice();
		    		reservedGas += unit.gasPrice();
				}
			}
    	}
    	supplyPerFrame += unit.supplyRequired()*1f/unit.buildTime();    	
    }
    
    /** Decide how to spend resources after finishing initial BO. 
     * It doesn't matter whether you can afford the next building, as it will just maintain priority until you can.
     */
    public UnitType transitionZ() {
    	UnitType nextBuilding = null;
    	
    	double pylonSupplyPerFrame = 0;
    	pylonSupplyPerFrame = self.incompleteUnitCount(UnitType.Protoss_Pylon)*UnitType.Protoss_Pylon.supplyProvided()/(1.0*UnitType.Protoss_Pylon.buildTime());
//    			+ self.incompleteUnitCount(UnitType.Protoss_Nexus)*UnitType.Protoss_Nexus.supplyProvided()/(1.0*UnitType.Protoss_Nexus.buildTime());
    	
    	if(pylonSupplyPerFrame <= supplyPerFrame && self.supplyUsed() >= self.supplyTotal() - 16 && self.supplyTotal() < 400) {
    		//don't get supply blocked
    		nextBuilding = UnitType.Protoss_Pylon;
    	} else if(self.allUnitCount(UnitType.Protoss_Assimilator) < gasBases) {
    		nextBuilding = UnitType.Protoss_Assimilator;
    	} else if(self.allUnitCount(UnitType.Protoss_Stargate) == 0
    		&& self.completedUnitCount(UnitType.Protoss_Cybernetics_Core) > 0) {
		
    		nextBuilding = UnitType.Protoss_Stargate;    	
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) <2) {
    		nextBuilding = UnitType.Protoss_Gateway;  
    	} else if(self.allUnitCount(UnitType.Protoss_Photon_Cannon) <3
    		&& self.allUnitCount(UnitType.Protoss_Forge) > 0
    		&& (enemyArmyTab.containsKey(UnitType.Zerg_Mutalisk) || enemyArmyTab.containsKey(UnitType.Zerg_Scourge) 
    		|| enemyBuildingTab.containsKey(UnitType.Zerg_Spire))) {
    		nextBuilding = UnitType.Protoss_Photon_Cannon;  
    	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Facility) == 0) {
        	if(self.allUnitCount(UnitType.Protoss_Cybernetics_Core) > 0) 
        		nextBuilding = UnitType.Protoss_Robotics_Facility; 	
    	} else if(enemyArmyTab.containsKey(UnitType.Zerg_Lurker) && self.allUnitCount(UnitType.Protoss_Observatory) == 0
            	&& self.allUnitCount(UnitType.Protoss_Robotics_Facility) > 0) {
        		
            	nextBuilding = UnitType.Protoss_Observatory;
    	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Support_Bay) == 0) { 
    		if(self.allUnitCount(UnitType.Protoss_Robotics_Facility) > 0) 
    			nextBuilding = UnitType.Protoss_Robotics_Support_Bay; 		
    	} else if(self.allUnitCount(UnitType.Protoss_Nexus) <2) {
    		nextBuilding = UnitType.Protoss_Nexus;  
    	} else if(self.allUnitCount(UnitType.Protoss_Observatory) == 0 
    		&& self.allUnitCount(UnitType.Protoss_Robotics_Facility) > 0) {
		
    		nextBuilding = UnitType.Protoss_Observatory;
    	} else if(self.allUnitCount(UnitType.Protoss_Forge) == 0) {
    		nextBuilding = UnitType.Protoss_Forge;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) <4) {
    		nextBuilding = UnitType.Protoss_Gateway;   
    	} else if(self.allUnitCount(UnitType.Protoss_Nexus) < 3 && gasBases <2) {
    		nextBuilding = UnitType.Protoss_Nexus;
    	} else if(self.allUnitCount(UnitType.Protoss_Citadel_of_Adun) == 0 
    		&& self.completedUnitCount(UnitType.Protoss_Cybernetics_Core) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Citadel_of_Adun;    	
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) <6) {
    		nextBuilding = UnitType.Protoss_Gateway;  
    	} else if(self.allUnitCount(UnitType.Protoss_Templar_Archives) == 0
    		&& self.allUnitCount(UnitType.Protoss_Citadel_of_Adun) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Templar_Archives;  
    	} else if(gasBases < 3) {
    		nextBuilding = UnitType.Protoss_Nexus;    	  	
//    	} else if(self.allUnitCount(UnitType.Protoss_Fleet_Beacon) == 0
//    		&& self.allUnitCount(UnitType.Protoss_Stargate) > 0) {
//    		
//    		nextBuilding = UnitType.Protoss_Fleet_Beacon;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) < Math.min(15, (patchCount*3)/8)) {
    		nextBuilding = UnitType.Protoss_Gateway;        		
    	} else {
    		nextBuilding = UnitType.Protoss_Nexus;    	
    	}
    	return nextBuilding;
    }
    /** Decide how to spend resources after finishing initial BO. 
     * It doesn't matter whether you can afford the next building, as it will just maintain priority until you can.
     */
    public UnitType transitionP() {
    	UnitType nextBuilding = null;
    	
    	double pylonSupplyPerFrame = 0;
    	pylonSupplyPerFrame = self.incompleteUnitCount(UnitType.Protoss_Pylon)*UnitType.Protoss_Pylon.supplyProvided()/(1.0*UnitType.Protoss_Pylon.buildTime());
//    			+ self.incompleteUnitCount(UnitType.Protoss_Nexus)*UnitType.Protoss_Nexus.supplyProvided()/(1.0*UnitType.Protoss_Nexus.buildTime());
    	
    	if(pylonSupplyPerFrame <= supplyPerFrame && self.supplyUsed() >= self.supplyTotal() - 16 && self.supplyTotal() < 400) {
    		//don't get supply blocked
    		nextBuilding = UnitType.Protoss_Pylon;
    	} else if(self.allUnitCount(UnitType.Protoss_Assimilator) < gasBases) {
    		nextBuilding = UnitType.Protoss_Assimilator;
    	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Facility) == 0) {
        	if(self.allUnitCount(UnitType.Protoss_Cybernetics_Core) > 0) 
        		nextBuilding = UnitType.Protoss_Robotics_Facility; 	
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) < 2) {
    		nextBuilding = UnitType.Protoss_Gateway; 
    	} else if(self.allUnitCount(UnitType.Protoss_Observatory) == 0) {
    		if(self.completedUnitCount(UnitType.Protoss_Robotics_Facility) > 0)    		
    			nextBuilding = UnitType.Protoss_Observatory;
    	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Support_Bay) == 0) {
    		if(self.completedUnitCount(UnitType.Protoss_Robotics_Facility) > 0)     			
    			nextBuilding = UnitType.Protoss_Robotics_Support_Bay; 		   		
    	} else if(self.allUnitCount(UnitType.Protoss_Nexus) < 2) {
    		nextBuilding = UnitType.Protoss_Nexus; 
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) < 4) {
    		nextBuilding = UnitType.Protoss_Gateway;   
    	} else if(self.allUnitCount(UnitType.Protoss_Nexus) < 3 && gasBases <2) {
    		nextBuilding = UnitType.Protoss_Nexus;
    	} else if(self.allUnitCount(UnitType.Protoss_Citadel_of_Adun) == 0) {    		
    		nextBuilding = UnitType.Protoss_Citadel_of_Adun;    
		} else if(self.allUnitCount(UnitType.Protoss_Templar_Archives) == 0) {	        		
			nextBuilding = UnitType.Protoss_Templar_Archives;    	
    	} else if(gasBases < 3) {
    		nextBuilding = UnitType.Protoss_Nexus;   	
    	} else if(self.allUnitCount(UnitType.Protoss_Forge) == 0) {
    		nextBuilding = UnitType.Protoss_Forge; 
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) < Math.min(15, (patchCount*3)/8)) {
    		nextBuilding = UnitType.Protoss_Gateway;        	
    	} else {
    		nextBuilding = UnitType.Protoss_Nexus;    	
    	}
    	return nextBuilding;
    }
    /** Decide how to spend resources after finishing initial BO. 
     * It doesn't matter whether you can afford the next building, as it will just maintain priority until you can.
     */
    public UnitType transitionT() {
    	UnitType nextBuilding = null;
    	
    	double pylonSupplyPerFrame = 0;
    	pylonSupplyPerFrame = self.incompleteUnitCount(UnitType.Protoss_Pylon)*UnitType.Protoss_Pylon.supplyProvided()/(1.0*UnitType.Protoss_Pylon.buildTime());
//    			+ self.incompleteUnitCount(UnitType.Protoss_Nexus)*UnitType.Protoss_Nexus.supplyProvided()/(1.0*UnitType.Protoss_Nexus.buildTime());
    	
    	if(pylonSupplyPerFrame <= supplyPerFrame && self.supplyUsed() >= self.supplyTotal() - 16 && self.supplyTotal() < 400) {
    		//don't get supply blocked
    		nextBuilding = UnitType.Protoss_Pylon;
    	} else if(self.allUnitCount(UnitType.Protoss_Assimilator) < gasBases) {
    		nextBuilding = UnitType.Protoss_Assimilator;
    	} else if(self.allUnitCount(UnitType.Protoss_Nexus) <2) {
    		nextBuilding = UnitType.Protoss_Nexus;   
    	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Facility) == 0) {
        	if(self.allUnitCount(UnitType.Protoss_Cybernetics_Core) > 0) 
        		nextBuilding = UnitType.Protoss_Robotics_Facility; 	
    	} else if(self.allUnitCount(UnitType.Protoss_Observatory) == 0) {
    		if(self.completedUnitCount(UnitType.Protoss_Robotics_Facility) > 0) 
    			nextBuilding = UnitType.Protoss_Observatory;
    	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Support_Bay) == 0) {
    		if(self.completedUnitCount(UnitType.Protoss_Robotics_Facility) > 0)
    			nextBuilding = UnitType.Protoss_Robotics_Support_Bay; 	
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) <4) {
    		nextBuilding = UnitType.Protoss_Gateway;  
    	} else if(self.allUnitCount(UnitType.Protoss_Nexus) <3) {
    		nextBuilding = UnitType.Protoss_Nexus;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Nexus) < 4 && gasBases <2) {
    		nextBuilding = UnitType.Protoss_Nexus;
    	} else if(self.allUnitCount(UnitType.Protoss_Citadel_of_Adun) == 0 
    		&& self.completedUnitCount(UnitType.Protoss_Cybernetics_Core) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Citadel_of_Adun;   
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) <7) {
    		nextBuilding = UnitType.Protoss_Gateway;   	
    	} else if(gasBases < 4) {
    		nextBuilding = UnitType.Protoss_Nexus;  
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) <10) {
    		nextBuilding = UnitType.Protoss_Gateway;   	
    	} else if(self.allUnitCount(UnitType.Protoss_Forge) == 0) {
    		nextBuilding = UnitType.Protoss_Forge; 		
    	} else if(self.allUnitCount(UnitType.Protoss_Templar_Archives) == 0
    		&& self.completedUnitCount(UnitType.Protoss_Citadel_of_Adun) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Templar_Archives;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Stargate) == 0
    		&& self.completedUnitCount(UnitType.Protoss_Cybernetics_Core) > 0
    		&& self.completedUnitCount(UnitType.Protoss_Templar_Archives) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Stargate;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Arbiter_Tribunal) == 0
    		&& self.completedUnitCount(UnitType.Protoss_Stargate) > 0
    		&& self.completedUnitCount(UnitType.Protoss_Templar_Archives) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Arbiter_Tribunal;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Templar_Archives) > 0 
    		&& self.allUnitCount(UnitType.Protoss_Gateway) < Math.min(15, (patchCount*3)/8)) {
    		nextBuilding = UnitType.Protoss_Gateway;        		
    	} else if(self.allUnitCount(UnitType.Protoss_Arbiter_Tribunal) > 0)  {
    		nextBuilding = UnitType.Protoss_Nexus;    	
    	}
    	return nextBuilding;
    }
    
    /** Tell workers when/where to gather
     * 
     */
    public void gather() {
    	MyBase bestBase = null;
    	MyBase worstBase = null;
    	for(Region region: bases.keySet()) {
    		MyBase base = bases.get(region);
    		//find maynard bases
    		if(base.getPatchCount() > 0 && base.isCompleted() && (bestBase == null || (base.getWorkers().getUnitCount()/base.getPatchCount() 
    			< bestBase.getWorkers().getUnitCount()/bestBase.getPatchCount()))) {
    			
    			bestBase = base;
    		}
    		if(base.getPatchCount() > 0 && base.isCompleted() && (worstBase == null || (base.getWorkers().getUnitCount()/base.getPatchCount() 
    			> (worstBase.getWorkers().getUnitCount()+1)/worstBase.getPatchCount()))) {
    			
    			worstBase = base;
    		}
    		
    		if(base.getPatchCount() > 0) {
	    		//mine with idle workers
	    		Iterator<MyUnit> itr = base.getWorkers().getUnits().iterator();
	    		MyUnit myUnit;
	    		while(itr.hasNext()) {
	    			myUnit = itr.next();
	    			if(myUnit.getUnit().isIdle()) {
	    				gatherMinerals(myUnit, base);
	    			}
	    		}
    		}
    		game.drawTextMap(region.getCenter(), ""+base.getWorkers().getUnitCount());
    	}
    	if(bestBase != null) {
    		//mine with new probes
	    	for(MyUnit myUnit: newWorkers.getUnits()) {
	    		gatherMinerals(myUnit, bestBase);
	    	}
	    	//maynard workers
	    	if(worstBase != null && worstBase != bestBase) {
	    		Iterator<MyUnit> itr = worstBase.getWorkers().getUnits().iterator();
	    		MyUnit myUnit;
	    		while(itr.hasNext() && bestBase.getWorkers().getUnitCount()/bestBase.getPatchCount() 
	        		< (worstBase.getWorkers().getUnitCount()-1)/worstBase.getPatchCount()) {
	    			myUnit = itr.next();
	    			if(myUnit.getUnit().isIdle() || (myUnit.getUnit().isGatheringMinerals() && !myUnit.getUnit().isCarryingMinerals())) {
	    				gatherMinerals(myUnit, bestBase);
	    				itr.remove();
	    			}
	    		}
	    	}
    	}
    	newWorkers.clearUnits();
//    	for (Unit myUnit : self.getUnits()) {
//            //if it's a worker and it's idle or just returned minerals, send it to the best mineral patch
//            if (myUnit.isCompleted() && myUnit.getType() == UnitType.Protoss_Probe) {
//            	if(myUnit.getTarget() != null) {
//            		game.drawTextMap(myUnit.getPosition(), myUnit.getTarget().getID()+"");
//           		}
//            	//hacky setup to call gatherMinerals exactly once per trip, right when minerals are returned
//            	if(myUnit.isIdle()) {
//            		gatherMinerals(myUnit, false);
//            	} else if (myUnit.isGatheringMinerals() && myUnit.isCarryingMinerals() && !myUnit.getLastCommand().isQueued()) {
//            		myUnit.returnCargo(false);
//            		myUnit.move(myUnit.getPosition(),true);
////            		myUnit.rightClick(myUnit.getLastCommand().getTarget(), true);
////            		game.drawLineMap(myUnit.getPosition(), myUnit.getLastCommand().getTarget().getPosition(), Color.White);
//            	} else if (myUnit.isGatheringMinerals() && !myUnit.isCarryingMinerals() && myUnit.getLastCommand().getUnitCommandType() == UnitCommandType.Move) {
//            		gatherMinerals(myUnit, false);
//            	}    
//            	if(myUnit.getTarget() != null) {
//            		game.drawTextMap(myUnit.getPosition(), myUnit.getTarget().getID()+"");
//           		}
            	//hacky setup to call gatherMinerals exactly once per trip, right when minerals are returned
//            	if(myUnit.isIdle()) {
//            		gatherMinerals(myUnit, false);
//            		game.drawTextMap(myUnit.getPosition(), "Idle");
//            	} else if (myUnit.isGatheringMinerals() && myUnit.isCarryingMinerals() && !myUnit.getLastCommand().isQueued()) {
//            		myUnit.returnCargo(false);
//            		gatherMinerals(myUnit, true);
//            	} else if (myUnit.isGatheringMinerals() && !myUnit.isCarryingMinerals() && myUnit.getLastCommand().isQueued()) {
//            		gatherMinerals(myUnit, false);
//            	}   
//            }
//        }    	
    	for (Unit myUnit : self.getUnits()) {
    		int gasCount = 0;
    		if(myUnit.getType() == UnitType.Protoss_Assimilator && myUnit.isCompleted()) {
    			//needs some work for expos with partial gas saturation
    			for (Unit u: game.getUnitsInRadius(myUnit.getPosition(), 6*PIXELS_PER_TILE)) {
    				if(u.getType().isWorker() && u.getPlayer().equals(self) && u.isGatheringGas()) {
    					gasCount++;
    				}
    			}     
    			if(myUnit.isBeingGathered())
    				gasCount++;
    			
	        	for (Unit u: game.getUnitsInRadius(myUnit.getPosition(), 6*PIXELS_PER_TILE)) {
	        		if(u.getType().isWorker() && u.getPlayer().equals(self)) {
		        		if (gasCount > gasCap) {
		        			if(u.isGatheringGas() && !u.isCarryingGas()) {
		        				gatherMinerals(u, false);
		        				gasCount--;
		        			}
		        		} else if (gasCount < gasCap && (u.isGatheringMinerals() && !u.isCarryingMinerals() || (u.isIdle() && !u.isGatheringGas()))) {	        			
		        			u.gather(myUnit, false);
		        			gasCount++;
		        		}
	        		}
	        	}
    		}
    	}
    }
    
    public void gatherMinerals(MyUnit myUnit, MyBase base) {
    	Unit bestMineral = null;
    	Unit closestLongDistance = null;
    	
    	int lowestSaturation = 999;
    	int saturation = 0;

        //find the closest mineral    	
        for(Unit neutralUnit : base.getResources().keySet()) {
            if(neutralUnit.getType().isMineralField() 
            	&& (bestMineral == null || base.getResources().get(neutralUnit) < base.getResources().get(bestMineral)
            	|| (base.getResources().get(neutralUnit) == base.getResources().get(bestMineral) 
            	&& neutralUnit.getPosition().getApproxDistance(myUnit.getPosition()) 
            	< bestMineral.getPosition().getApproxDistance(myUnit.getPosition())))) {
            		
            	bestMineral = neutralUnit;            	
            }
        }
        if(bestMineral != null) {
	        myUnit.getUnit().gather(bestMineral);
        } else {
			for (Unit neutralUnit : game.neutral().getUnits()) {
				//long distance mining
				if(closestLongDistance == null || neutralUnit.getPosition().getApproxDistance(myUnit.getPosition())
					< closestLongDistance.getPosition().getApproxDistance(myUnit.getPosition())) {
					closestLongDistance = neutralUnit;
				}			
			}
			myUnit.getUnit().gather(closestLongDistance);
        }
        if(!base.getWorkers().getUnits().contains(myUnit))
        	base.add(myUnit);        
    }
    
    public void gatherMinerals(Unit myUnit, boolean shiftQ) {
    	Unit closestMineral = null;
    	Unit closestLongDistance = null;
    	
    	int lowestSaturation = 999;
    	int saturation = 0;
    	boolean hasBase = false;
        //find the closest mineral    	
        for (Unit neutralUnit : game.neutral().getUnits()) {
            if (neutralUnit.getType().isMineralField()) {            	
            	saturation = 0;
            	hasBase = false;
            	for (Unit u: game.getUnitsInRadius(neutralUnit.getPosition(), 6*PIXELS_PER_TILE)) {
            		if(u.getPlayer().equals(self)) {
	            		if(u.getType() == UnitType.Protoss_Probe) {
	            			if(u.getTarget() != null && u.getTarget().getID() == neutralUnit.getID()) {	         
	            				saturation++;
	            			}	            			
	            		} else if(u.getType().isResourceDepot() && (u.isCompleted()
	            			|| u.getRemainingBuildTime() <= u.getPosition().getDistance(myUnit.getPosition())/myUnit.getType().topSpeed())) {
	            			
	            			hasBase = true;
	            		}
            		}
            	}
            	
            	//long distance mining
            	if(closestLongDistance == null || neutralUnit.getPosition().getApproxDistance(myUnit.getPosition())
            		< closestLongDistance.getPosition().getApproxDistance(myUnit.getPosition())) {
            		closestLongDistance = neutralUnit;
            	}
            	//game.drawTextMap(neutralUnit.getPosition(), saturation+"");
            	if(hasBase) {
            		if(saturation == 0 && lowestSaturation >= 2) {
            			//maynarding only
            			lowestSaturation = saturation;
            			closestMineral = neutralUnit;
            		} else if(saturation < lowestSaturation && myUnit.getPosition().getApproxDistance(neutralUnit.getPosition()) < 10*PIXELS_PER_TILE) {
            			lowestSaturation = saturation;
            			closestMineral = neutralUnit;             			
            		} else if(saturation < lowestSaturation+1 
            			&& (closestMineral == null || myUnit.getPosition().getDistance(neutralUnit.getPosition()) + 10*PIXELS_PER_TILE
            			< myUnit.getPosition().getDistance(closestMineral.getPosition()))) {
            			
            			lowestSaturation = saturation;
            			closestMineral = neutralUnit;            			
            		} else if(saturation <= lowestSaturation
            			&& (closestMineral == null || (myUnit.getPosition().getApproxDistance(neutralUnit.getPosition()) 
            			< myUnit.getPosition().getApproxDistance(closestMineral.getPosition())))) {
            			
            			lowestSaturation = saturation;
            			closestMineral = neutralUnit;           			
            		}
            	}
            }
        }

        //if a mineral patch was found, send the worker to gather it
        if (closestMineral != null) {
            myUnit.gather(closestMineral, shiftQ);
//            Region region = BWTA.getRegion(closestMineral.getPosition());
//            if(bases.containsKey(region))
//            	if(bases.get(region).get)
        } else if(closestLongDistance != null) {
        	myUnit.gather(closestLongDistance);
        }
    }
    
    /** Find the enemy main and scout with probe
     * 
     */
    public void scout(Unit myUnit) throws Exception {
    	if(enemyMain == null) {
	    	TilePosition farthestMain = null;
	    	for(TilePosition t: game.getStartLocations()) {
	    		if(!game.isExplored(t) && (farthestMain == null
	    			|| t.getDistance(self.getStartLocation()) > farthestMain.getDistance(self.getStartLocation()))) {
	    			
	    			farthestMain = t;
	    		}
	    	}
	    	myUnit.move(farthestMain.toPosition());
    	} else {
    		BaseLocation likelyExpo = null;
        	if(likelyExpo == null) {
        		//after checking all explored bases, go back and check ones that aren't visible
        		int scoutRange = 5;
        		int revisitTime = 500;
            	for(BaseLocation b: BWTA.getBaseLocations()) {
    	    		if(!(b.isIsland() && !myUnit.isFlying())
    	    			&& (visionMap[b.getTilePosition().getX()+2][b.getTilePosition().getY()+1] > revisitTime
    	    			|| visionMap[b.getTilePosition().getX()+2-scoutRange][b.getTilePosition().getY()+1] > revisitTime
    	    			|| visionMap[b.getTilePosition().getX()+2+scoutRange][b.getTilePosition().getY()+1] > revisitTime
    	    			|| visionMap[b.getTilePosition().getX()+2][b.getTilePosition().getY()+1-scoutRange] > revisitTime
    	    			|| visionMap[b.getTilePosition().getX()+2][b.getTilePosition().getY()+1+scoutRange] > revisitTime)   	    			
    	    			&& (likelyExpo == null || b.getDistance(enemyMain.toPosition()) 
    	    			< likelyExpo.getDistance(enemyMain.toPosition()))) {
    	    			
    	    			likelyExpo = b;
    	    		}
    	    	}
        	}
        	if(likelyExpo == null) {
        		//after checking all bases, go back and check old ones
            	for(BaseLocation b: BWTA.getBaseLocations()) {
    	    		if(!(b.isIsland() && !myUnit.isFlying()) && 
    	    			(likelyExpo == null || b.getDistance(enemyMain.toPosition()) 
    	    			< likelyExpo.getDistance(enemyMain.toPosition()))) {
    	    			
    	    			likelyExpo = b;
    	    		}
    	    	}
        	}
//        	game.drawBoxMap(likelyExpo.getTilePosition().toPosition(), likelyExpo.getPosition(), Color.Yellow);
        	
        	double scale = 32*5;
        	double[] moveVector = {0,0};
        	

        	double d = myUnit.getPosition().getApproxDistance(likelyExpo.getPosition());
         	double[] scoutVector = {(likelyExpo.getX() - myUnit.getX())*scale/d,(likelyExpo.getY() - myUnit.getY())*scale/d};
         	double[] targetVector = {0,0};
         	double[] threatVector = {0,0};
         	double[] terrainVector = {0,0};
         	double[] clusterVector = {0,0};
        	double[] attackVector = {0,0};   
        	
        	//fight visible air units instead of scout
        	if(myUnit.getType() == UnitType.Protoss_Corsair && airAttackPosition != null) {
        		d = myUnit.getPosition().getApproxDistance(airAttackPosition);
        		attackVector = new double[]{(airAttackPosition.getX() - myUnit.getX())*scale/d,(airAttackPosition.getY() - myUnit.getY())*scale/d};
        	}
         	double hisRange;
         	double myRange;      	
         	
         	double mySize = Math.sqrt(Math.pow(myUnit.getType().width(),2) + Math.pow(myUnit.getType().height(),2))/2;
         	double hisSize;
         	double detectFactor = 1;
         	double bestTargetScore = 0;
         	double targetScore = 0;
    		for(Unit hisUnit: myUnit.getUnitsInRadius(myUnit.getType().sightRange())) {
				if(hisUnit.getPlayer().equals(game.enemy())				
					&& (!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)) {
					d = myUnit.getPosition().getApproxDistance(hisUnit.getPosition());
					hisSize = Math.sqrt(Math.pow(hisUnit.getType().width(),2) + Math.pow(hisUnit.getType().height(),2))/2;	 
					
	    			if(myUnit.getType() == UnitType.Protoss_Corsair && !hisUnit.getType().airWeapon().equals(WeaponType.None)) {
	    				hisRange = game.enemy().weaponMaxRange(hisUnit.getType().airWeapon()) + hisSize + mySize;
	    				
	    				//potential function is 1/d^2
	    				if(!hisUnit.isFlying()) {	    				
	    					threatVector[0] += -2*scale*hisRange/d/d*(hisUnit.getX()-myUnit.getX());
	    					threatVector[1] += -2*scale*hisRange/d/d*(hisUnit.getY()-myUnit.getY());
	    				} else {
	    					threatVector[0] += -0.6*scale*hisRange/d/d*(hisUnit.getX()-myUnit.getX());
	    					threatVector[1] += -0.6*scale*hisRange/d/d*(hisUnit.getY()-myUnit.getY());
	    				}
	    			} else if(myUnit.getType() == UnitType.Protoss_Observer
	    				&& (!hisUnit.getType().airWeapon().equals(WeaponType.None) || hisUnit.getType().isDetector())) {		    				
	    				  
	    				if(hisUnit.getType().isDetector()) {
	    					hisRange = hisUnit.getType().sightRange() + hisSize + mySize;
		    				threatVector[0] += -2*scale*hisRange/d/d*(hisUnit.getX()-myUnit.getX());
	    					threatVector[1] += -2*scale*hisRange/d/d*(hisUnit.getY()-myUnit.getY());
	    				} else {
	    					if(myUnit.isDetected())
	    						detectFactor = 2;
	    					hisRange = game.enemy().weaponMaxRange(hisUnit.getType().airWeapon()) + hisSize + mySize;	  
	    					threatVector[0] += -1*detectFactor*scale*hisRange/d/d*(hisUnit.getX()-myUnit.getX());
	    					threatVector[1] += -1*detectFactor*scale*hisRange/d/d*(hisUnit.getY()-myUnit.getY());
	    				}
	    			} else if (myUnit.getType() == UnitType.Protoss_Probe && (!hisUnit.getType().groundWeapon().equals(WeaponType.None)
	    				|| hisUnit.getType() == UnitType.Terran_Bunker) && (!hisUnit.getType().isWorker() || hisUnit.isAttacking())) {
	    				hisRange = game.enemy().weaponMaxRange(hisUnit.getType().groundWeapon()) + hisSize + mySize;	 
	    				threatVector[0] += -2*scale*hisRange/d/d*(hisUnit.getX()-myUnit.getX());
    					threatVector[1] += -2*scale*hisRange/d/d*(hisUnit.getY()-myUnit.getY());
	    			}
	    			
	    			if(myUnit.getType() == UnitType.Protoss_Corsair && hisUnit.isFlying()) {
	    				myRange = self.weaponMaxRange(myUnit.getType().airWeapon());
	    				targetScore = 1.25*scale*myRange/d;
	    				if(hisUnit.getType().airWeapon() != WeaponType.None)
	    					targetScore *= 1.5;
	    				if(targetScore > bestTargetScore) {
	    					targetVector[0] = targetScore/d*(hisUnit.getX()-myUnit.getX());
	    					targetVector[1] = targetScore/d*(hisUnit.getY()-myUnit.getY());
	    					bestTargetScore = targetScore;
	    				}
	    			}
				}
    		}	
    		
    		Unit cloakedTarget = null;
    		//observers have global priority to detect
    		if(myUnit.getType() == UnitType.Protoss_Observer) {
        		for(Unit hisUnit: game.enemy().getUnits()) {		
					if(hisUnit.isCompleted() && hisUnit.isVisible()
	    				&& (hisUnit.getType().hasPermanentCloak() || hisUnit.getType().isCloakable() 
	    				|| hisUnit.isBurrowed()|| hisUnit.getType() == UnitType.Zerg_Lurker)
	    				&& (cloakedTarget == null || safety(hisUnit.getPosition()) > safety(cloakedTarget.getPosition()))) {
						
						cloakedTarget = hisUnit;
					} 
        		}	
    		}
    		if(cloakedTarget != null) {
				d = myUnit.getPosition().getApproxDistance(cloakedTarget.getPosition());
				targetVector[0] += 3*scale/d*(cloakedTarget.getX()-myUnit.getX());
				targetVector[1] += 3*scale/d*(cloakedTarget.getY()-myUnit.getY());
    		}
         	
         	int hitsToKillHim;
         	Unit weakestAirEnemy = null;
         	int leastHits = 9999;       
         	boolean safeToAttack = false;
         	int clusterCount = 1;
    		
    		for(Unit u: self.getUnits()) {
    			if(u.isCompleted() && u.getType() == myUnit.getType() && u.getID() != myUnit.getID()) {    				
    				if(myUnit.getType() == UnitType.Protoss_Observer) {
    					d = myUnit.getPosition().getApproxDistance(u.getPosition());
    					clusterVector[0] += -1*scale*myUnit.getType().sightRange()/d/d*(u.getX()-myUnit.getX());
    					clusterVector[1] += -1*scale*myUnit.getType().sightRange()/d/d*(u.getY()-myUnit.getY());
    				} else if(myUnit.getType() == UnitType.Protoss_Corsair) {
    					d = myUnit.getPosition().getApproxDistance(u.getLastCommand().getTargetPosition());
    					clusterVector[0] += 0.2*scale/d*(u.getLastCommand().getTargetPosition().getX()-myUnit.getX());
    					clusterVector[1] += 0.2*scale/d*(u.getLastCommand().getTargetPosition().getY()-myUnit.getY());
    					if(d < scale) {
    						clusterCount++;
    					}
    				}
    			}
    		}
    		
    		double[] retreatVector = {0,0};
    		if(myUnit.getType() == UnitType.Protoss_Corsair 
    			&& (enemyArmyTab.containsKey(UnitType.Zerg_Mutalisk) && enemyArmyTab.get(UnitType.Zerg_Mutalisk) 
    			> self.allUnitCount(UnitType.Protoss_Corsair) * 3
    			|| (enemyArmyTab.containsKey(UnitType.Zerg_Scourge) && clusterCount < 6))) {
    			
    			d = myUnit.getPosition().getApproxDistance(self.getStartLocation().toPosition());
    			if(d > 3*32) {
	    			retreatVector[0] = 2*scale/d*(game.self().getStartLocation().toPosition().getX()-myUnit.getX());
	    			retreatVector[1] = 2*scale/d*(game.self().getStartLocation().toPosition().getY()-myUnit.getY());	
    			}
    		}
    		
    		//finalize vector
    		moveVector[0] = threatVector[0] + clusterVector[0];
    		moveVector[1] = threatVector[1] + clusterVector[1];
    		boolean hasCommand = false;
    		if(targetVector[0] == 0 && targetVector[1] == 0) {
    			if(attackVector[0] == 0 && attackVector[1] == 0) {
    				if(myUnit.isFlying()) {
	    				moveVector[0] += scoutVector[0];
	    				moveVector[1] += scoutVector[1];
    				} else {
    					if(moveVector[0] == 0 && moveVector[1] == 0) {
    						myUnit.move(likelyExpo.getPosition());
    						hasCommand = true;
    					} else {
    						moveVector[0] += scoutVector[0];
    	    				moveVector[1] += scoutVector[1];
    					}
    				}
    			} else {
    				moveVector[0] += attackVector[0];
    				moveVector[1] += attackVector[1];
    			}
    		} else if(retreatVector[0] == 0 && retreatVector[1] == 0) {
    			targetVector[0] *= clusterCount;
    			targetVector[1] *= clusterCount;
    			moveVector[0] += targetVector[0];
    			moveVector[1] += targetVector[1];
    		} else {
    			moveVector[0] += retreatVector[0];
    			moveVector[1] += retreatVector[1];
    		}
    		
         	//don't let air units get stuck in corners
         	if(myUnit.isFlying()) {
         		for(int x=0; x<=(game.mapWidth())*32-1; x+=(game.mapWidth())*32-1) {
         			for(int y=0; y<=(game.mapHeight())*32-1; y+=(game.mapHeight())*32-1) {
         				//walkable uses mini-tiles
         				//if(game.isWalkable(x/8, y/8)) {
         					Position cornerPos = new Position(x,y);
//         					game.drawCircleMap(x, y, (int) scale + 32, Color.Red);
         					d = cornerPos.getApproxDistance(myUnit.getPosition());
         					double rectDist = Math.abs(x-myUnit.getX()) + Math.abs(y-myUnit.getY());
	    					terrainVector[0] += -3*scale*scale/rectDist/rectDist*(x-myUnit.getX());
	    					terrainVector[1] += -3*scale*scale/rectDist/rectDist*(y-myUnit.getY());
         				//}
         			}
         		}
         	} else {
         		//don't walk into walls
         		int tileX = myUnit.getTilePosition().getX();
         		int tileY = myUnit.getTilePosition().getY();
         		int xPos;
         		int yPos;
         		if(walkMap[tileX][tileY] == 0) {
         			for(int x=tileX-1; x<= tileX+1; x++) {
         				for(int y=tileY-1;y<=tileY+1;y++) {
         					if(x>=0 && x<game.mapWidth() && y>=0 && y<=game.mapHeight() && x != tileX && y != tileY) {
	         					xPos = x*32 + 16;
	         					yPos = y*32 + 16;
	         					//tile is in direction of movement
	         					if(((xPos-myUnit.getX())*moveVector[0] + (yPos-myUnit.getY())*moveVector[1])>= 0) {
	         						//check for stuff in the way
	         						if(GuiBot.walkMap[x][y] > 0 || game.getUnitsOnTile(x, y).size() > 0) {
	        	     					d = myUnit.getPosition().getApproxDistance(new Position(xPos, yPos));
	        	     					terrainVector[0] -= (xPos-myUnit.getX())/d;
	        	     					terrainVector[1] -= (yPos-myUnit.getY())/d;
	        				 		} 
	         					}
//         					if(x>=0 && x<game.mapWidth() && y>=0 && y<=game.mapHeight()) {
//	         					xPos = x*32 + 16;
//	         					yPos = y*32 + 16;
//	         					d = myUnit.getPosition().getApproxDistance(new Position(xPos, yPos));
//	         					terrainVector[0] += -(walkMap[x][y]-walkMap[tileX][tileY])*(xPos-myUnit.getX())/d;
//	         					terrainVector[1] += -(walkMap[x][y]-walkMap[tileX][tileY])*(yPos-myUnit.getY())/d;
//         					}
         					}
         				}
         			}
         		}
         		terrainVector = setMagnitude(terrainVector, 1);
         		terrainVector = setMagnitude(terrainVector, -terrainVector[0]*moveVector[0]-terrainVector[1]*moveVector[1]);
//         		game.drawLineMap(myUnit.getPosition(), new Position(myUnit.getX() + (int)terrainVector[0], myUnit.getY() + (int)terrainVector[1]), Color.White);
         	}
         	
         	moveVector[0] += terrainVector[0];
         	moveVector[1] += terrainVector[1];
         	
    		moveVector = setMagnitude(moveVector, scale);
    		moveVector = adjustForWalls(moveVector, myUnit);
    		
	    	if(myUnit.getType() == UnitType.Protoss_Corsair) {	
	    		//choose the best units to attack
	    		for(Unit hisUnit: myUnit.getUnitsInRadius(self.weaponMaxRange(myUnit.getType().airWeapon()) + 32)) {
					if(hisUnit.getPlayer().equals(game.enemy())	&& hisUnit.isCompleted() && hisUnit.isFlying() && myUnit.isInWeaponRange(hisUnit)) {
						d = myUnit.getPosition().getApproxDistance(hisUnit.getPosition());
//						safeToAttack = ((hisUnit.getX()-myUnit.getX())*moveVector[0] + (hisUnit.getY()-myUnit.getY())*moveVector[1]> 0);
						safeToAttack = ((hisUnit.getX()-myUnit.getX())*moveVector[0] + (hisUnit.getY()-myUnit.getY())*moveVector[1]> d*scale*Math.cos(Math.PI/4));
						hitsToKillHim = (int) Math.ceil(hisUnit.getHitPoints()/(double)game.getDamageTo(hisUnit.getType(), myUnit.getType(),game.enemy()));
						
						if(safeToAttack && hitsToKillHim < leastHits) {	        						
							weakestAirEnemy = hisUnit;
							leastHits = hitsToKillHim;
						}
					}
	    		}
    		}
	    	if(!hasCommand) {
	    		boolean kiteForward = false;
	    		if(weakestAirEnemy != null) {
	    			targetVector = new double[]{weakestAirEnemy.getX() - myUnit.getX(), weakestAirEnemy.getY() - myUnit.getY()};
		    		targetVector = setMagnitude(targetVector, scale);
		    		threatVector = setMagnitude(threatVector, scale);
		    		threatVector = adjustForWalls(threatVector, myUnit);
		    		kiteForward = (threatVector[0]*targetVector[0] + threatVector[1]*targetVector[1] > scale*scale*Math.cos(Math.PI/4));
	    		} 
				if(weakestAirEnemy != null && myUnit.getAirWeaponCooldown() == 0) {
					if(!(myUnit.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Unit 
						&& myUnit.getLastCommand().getTarget().equals(weakestAirEnemy)))
						myUnit.attack(weakestAirEnemy);
//					game.drawLineMap(myUnit.getX(), myUnit.getY(), weakestAirEnemy.getX(), weakestAirEnemy.getY(), Color.Red);
//					game.drawLineMap(myUnit.getX(), myUnit.getY(), myUnit.getX()+(int)moveVector[0], myUnit.getY()+(int)moveVector[1], Color.Green);
				} else if(weakestAirEnemy != null && myUnit.getAirWeaponCooldown() > 0) {
					if(!myUnit.isAttackFrame() && (kiteForward || !myUnit.isInWeaponRange(weakestAirEnemy)))
//						myUnit.move(weakestAirEnemy.getPosition());
						myUnit.move(new Position(myUnit.getX()+(int)moveVector[0], myUnit.getY()+(int)moveVector[1]));
//					game.drawLineMap(myUnit.getX(), myUnit.getY(), weakestAirEnemy.getX(), weakestAirEnemy.getY(), Color.Red);
//					game.drawLineMap(myUnit.getX(), myUnit.getY(), myUnit.getX()+(int)moveVector[0], myUnit.getY()+(int)moveVector[1], Color.Green);
				} else {
					myUnit.move(new Position(myUnit.getX()+(int)moveVector[0], myUnit.getY()+(int)moveVector[1]));
//					game.drawLineMap(myUnit.getX(), myUnit.getY(), myUnit.getX()+(int)moveVector[0], myUnit.getY()+(int)moveVector[1], Color.Green);
				}      	
	    	}
    	}
    }
    
    /**	Sets the magnitude of a vector without changing its direction
     * 
     * @param v		vector to modify
     * @param scale	desired magnitude
     * @return		vector scaled to magnitude
     */
    public static double[] setMagnitude(double[] v, double scale) {
    	if(v[0] == 0 && v[1] == 0 || scale <0)
    		return v;
    	else if(scale == 0)
    		return new double[] {0,0};
    	else
    		return new double[] {scale*v[0]/Math.sqrt(v[0]*v[0] + v[1]*v[1]),scale*v[1]/Math.sqrt(v[0]*v[0] + v[1]*v[1])};
    }
    
    /** Makes hovering units move at full speed even if they reach the end of the map.
     * 
     * @param v			vector to modify
     * @param myUnit	unit who's moving
     * @return			vector pointing somewhere in bounds of the map
     */
    public double[] adjustForWalls(double[] v, Unit myUnit) {
    	double r = Math.sqrt(v[0]*v[0] + v[1]*v[1]);
    	double margin = Math.max(myUnit.getType().width(), myUnit.getType().height())/2.0;
    	double dx = v[0];
    	double dy = v[1];
    	double dir;
    	boolean fixed = false;
    	double xOutside = 0;
    	double yOutside = 0;
    	
    	//fix literal corner cases
    	if(myUnit.getX() + dx < margin) {
    		xOutside = Math.abs(myUnit.getX() + dx - margin);
    	} else if(myUnit.getX() + dx > game.mapWidth()*32 - margin) {
    		xOutside = Math.abs(myUnit.getX() - (game.mapWidth()*32 - margin));
    	} else if(myUnit.getY() + dy < margin) {
    		yOutside = Math.abs(myUnit.getY() + dy - margin);
    	} else if(myUnit.getY() + dy > game.mapHeight()*32 - margin) {
    		yOutside = Math.abs(myUnit.getY() + dy - (game.mapHeight()*32 - margin));
    	} else {
    	}
    	if(xOutside > 0 && yOutside > 0) {
	    	if(xOutside > yOutside) {
	    		dy *= -1;
	    	} else {
	    		dx *= -1;
	    	}
    	}
    	
    	//redirect move vector to stay in map
    	if(myUnit.getX() + dx < margin) {
    		dir = Math.signum(dy);
    		if(dir == 0)
    			dir = Math.signum(myUnit.getX()-game.mapWidth()*32);
    		dx = margin-myUnit.getX();
    		dy = dir*Math.sqrt(r*r-dx*dx);
    	} else if(myUnit.getX() + dx > game.mapWidth()*32 - margin) {
    		dir = Math.signum(dy);
    		if(dir == 0)
    			dir = Math.signum(myUnit.getX()-game.mapWidth()*32);
    		dx = game.mapWidth()*32 - margin - myUnit.getX();
    		dy = dir*Math.sqrt(r*r-dx*dx);
    	} else if(myUnit.getY() + dy < margin) {
    		dir = Math.signum(dx);
    		if(dir == 0)
    			dir = Math.signum(myUnit.getY()-game.mapHeight()*32);
    		dy = margin - myUnit.getY();
    		dx = dir*Math.sqrt(r*r-dy*dy);
    	} else if(myUnit.getY() + dy > game.mapHeight()*32 - margin) {
    		dir = Math.signum(dx);
    		if(dir == 0)
    			dir = Math.signum(myUnit.getY()-game.mapHeight()*32);
    		dy = game.mapHeight()*32 - margin - myUnit.getY();
    		dx = dir*Math.sqrt(r*r-dy*dy);
    	} else {
    	}
    	return new double[] {dx, dy};
    }
    
    /** Micro army to optimize army value and gather at certain locations
     * 
     */
    public void controlArmy() throws Exception {
    	//default attack position if you can't see anything    	
	       	
    	Unit closestSquishy = null;
    	Unit closestEnemy = null;
    	HisUnit closestTurret = null;
    	Position center = null;
    	HisUnit hisTurret = null;
    	boolean aBaseIsUnderAttack = false;
    	boolean canAttackAir = false;
    	
    	int range = 9*32;
    	if(enemyBuildingTab.containsKey(UnitType.Terran_Siege_Tank_Siege_Mode))
    		range = 14*32;
    	
    	for(Squad squad: army) {
    		squad.setSeesRangedEnemies(false);
    		closestTurret = null;
    		closestSquishy = null;
    		closestEnemy = null;
    		canAttackAir = squad.canAttackAir();
    		if(squad.getUnits().size() > 0) {
    			center = squad.findCenter();
    			//tanks, static def, and bunkers
    			for(int ID: enemyBuildings.keySet()) {
    				hisTurret = enemyBuildings.get(ID);
    				if((!hisTurret.getType().isBuilding() || hisTurret.getType().canAttack() 
    					|| hisTurret.getType() == UnitType.Terran_Bunker) && hisTurret.getType() != UnitType.Resource_Vespene_Geyser
    					&& hisTurret.getPosition() != null && hisTurret.getPosition().getApproxDistance(center) <= 15*32) {
    					if(hisTurret.getPosition() != null && (closestTurret == null ||
    						hisTurret.getPosition().getApproxDistance(center) - hisTurret.getRange()
    						< closestTurret.getPosition().getApproxDistance(center) - closestTurret.getRange())) {
    						
    						closestTurret = hisTurret;
    					}
    					
    					squad.setSeesRangedEnemies(true);
    				}
    			}
    			//find closest enemies to squad
				for(Unit hisUnit: game.getUnitsInRadius(center, 15*32)) { 				
					if(hisUnit.getPlayer().equals(game.enemy())// && hisUnit.isDetected() 
						&& !hisUnit.isInvincible() && hisUnit.getType() != UnitType.Zerg_Larva
						&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Resource_Vespene_Geyser) {
						
						if(!hisUnit.isFlying() || canAttackAir) {
							if((!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)
								&& (closestSquishy == null || hisUnit.getPosition().getApproxDistance(center) 
								< closestSquishy.getPosition().getApproxDistance(center))) {
								
								closestSquishy = hisUnit;
							}
							if(closestEnemy == null || hisUnit.getPosition().getApproxDistance(center) 
								< closestEnemy.getPosition().getApproxDistance(center)) {
								
								closestEnemy = hisUnit;
							}
						}
						
						//figure out if enemy has ranged units so you can stop holding choke
						if(hisUnit.getType().groundWeapon() != WeaponType.None && game.enemy().weaponMaxRange(hisUnit.getType().groundWeapon()) > 32
							&& (center.getApproxDistance(hisUnit.getPosition()) <= game.enemy().weaponMaxRange(hisUnit.getType().groundWeapon()) + 32
							|| hisUnit.isAttacking()))
							squad.setSeesRangedEnemies(true);
					}
				}
				
				//prioritize units over buildings
				if(closestSquishy != null)
					closestEnemy = closestSquishy;
	    		if(closestTurret != null) {
	    			//pick closest enemy/turret based on its location and range
	    			if(closestEnemy != null) {
		    			if(closestTurret.getUnit().getDistance(center) < closestEnemy.getDistance(center)) {	
		    				squad.setObjective(closestTurret.getPosition());
		    			} else {
		    				squad.setObjective(closestEnemy.getPosition());
		    			}
	    			} else {			
	    				squad.setObjective(closestTurret.getPosition());
	    			}
	//    		} else if(closestSquishy != null && (center == null || closestSquishy.hasPath(center)))
	//	    		squad.setObjective(closestSquishy.getPosition());
	    		} else if(closestEnemy != null)
		    		squad.setObjective(closestEnemy.getPosition());
		    	else {
		    		if(airAttackPosition != null &&
		    			squad.canAttackAir() && airAttackPosition.getApproxDistance(center) < attackPosition.getApproxDistance(center))
		    			squad.setObjective(airAttackPosition);
		    		else
		    			squad.setObjective(attackPosition);
		    	}
		    	
		    	game.drawLineMap(new Position(squad.getObjective().getX() - 5,  squad.getObjective().getY() - 5), 
	        		new Position(squad.getObjective().getX() + 5,  squad.getObjective().getY() + 5), Color.Green);
	        	game.drawLineMap(new Position(squad.getObjective().getX() + 5,  squad.getObjective().getY() - 5), 
	            	new Position(squad.getObjective().getX() - 5,  squad.getObjective().getY() + 5), Color.Green);
    		}
    	}
    	if(attackPosition != null) {
	    	game.drawLineMap(new Position(attackPosition.getX() - 5,  attackPosition.getY() - 5), 
	    		new Position(attackPosition.getX() + 5,  attackPosition.getY() + 5), Color.Red);
	    	game.drawLineMap(new Position(attackPosition.getX() + 5,  attackPosition.getY() - 5), 
	        	new Position(attackPosition.getX() - 5,  attackPosition.getY() + 5), Color.Red);
    	}
        if(airAttackPosition != null) {
	    	game.drawLineMap(new Position(airAttackPosition.getX() - 5,  airAttackPosition.getY() - 5), 
	    		new Position(airAttackPosition.getX() + 5,  airAttackPosition.getY() + 5), Color.Cyan);
	    	game.drawLineMap(new Position(airAttackPosition.getX() + 5,  airAttackPosition.getY() - 5), 
	        	new Position(airAttackPosition.getX() - 5,  airAttackPosition.getY() + 5), Color.Cyan);
        }
        if(dropBase != null) {
	    	game.drawLineMap(new Position(dropBase.getPosition().getX() - 5,  dropBase.getPosition().getY() - 5), 
	    		new Position(dropBase.getPosition().getX() + 5,  dropBase.getPosition().getY() + 5), Color.Yellow);
	    	game.drawLineMap(new Position(dropBase.getPosition().getX() + 5,  dropBase.getPosition().getY() - 5), 
	        	new Position(dropBase.getPosition().getX() - 5,  dropBase.getPosition().getY() + 5), Color.Yellow);
        }
    	//army commands go here
    	
    	//probe pulling
    	MyBase base;
    	Squad workers;
    	for(Region r: bases.keySet()) {
    		base = bases.get(r);
    		workers = base.getWorkers();
    		center =base.getWorkers().findCenter();
    		if(workers.getUnits().size() > 0 && base.isUnderAttack() && attackPosition.getApproxDistance(center)< 8*32) {
    			//find closest enemies to squad
				for(Unit hisUnit: game.getUnitsInRadius(center, 8*32)) { 				
					if(hisUnit.getPlayer().equals(game.enemy()) && !hisUnit.isFlying() && hisUnit.isDetected()
						&& !hisUnit.isInvincible() && hisUnit.getType() != UnitType.Zerg_Larva
						&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Resource_Vespene_Geyser) {
						
						if((!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)
							&& (closestSquishy == null || hisUnit.getPosition().getApproxDistance(center) 
							< closestSquishy.getPosition().getApproxDistance(center))) {
							
							closestSquishy = hisUnit;
						}
					}
				}
    			if(closestSquishy != null) {
    				workers.setObjective(closestSquishy.getPosition());
    				workers.attack(range);
    			}
    		} else if(workers.getCommand() != UnitState.MINING) {
    			workers.resumeMining();
    		}
    		if(base.isUnderAttack()) {
    			aBaseIsUnderAttack = true;
    		}
    	}
    	
    	boolean allAttacking = true;
    	Iterator<Squad> squadItr = army.iterator();
    	Squad squad = null;
    	while(squadItr.hasNext()) {
    		squad = squadItr.next();
    		if(squad.getUnits().size() > 0) {
    			center = squad.findCenter();
		    	if(aBaseIsUnderAttack || (armySupply > enemyArmySupply &&
		    		(squad.getUnits().size() >= 12 || squad.getCommand() == UnitState.ATTACKING
		    		|| self.supplyUsed() >= 380 
	//	    		|| (center.getApproxDistance(squad.getObjective()) < 10*32 && squad.getUnitCount(UnitType.Protoss_Zealot) == 0)
		    		|| squad.seesRangedEnemies() && center.getApproxDistance(defenseChoke.getCenter()) < 10*32
		    		|| (enemyRace == Race.Terran && self.getUpgradeLevel(UpgradeType.Singularity_Charge) > 0 
		    			&& !enemyBuildingTab.containsKey(UnitType.Terran_Siege_Tank_Siege_Mode)
		    			&& game.enemy().getUpgradeLevel(UpgradeType.Ion_Thrusters) == 0)
		    		|| (enemyRace == Race.Protoss && self.allUnitCount(UnitType.Protoss_Nexus) > 1))
		    		)) {
		    		//recruit nearby idle units
		    		Iterator<MyUnit> itr = freeAgents.getUnits().iterator();
		    		MyUnit freeUnit;
		    		while(itr.hasNext()) {
		    			freeUnit = itr.next();
		    			if(freeUnit.getPosition().getApproxDistance(center) <= 10*32)
		    				squad.takeUnit(freeUnit, itr);
		    		}
		    		
		    		//exile stragglers from squad
		    		itr = squad.getUnits().iterator();
		    		MyUnit myUnit;
		    		while(itr.hasNext()) {
		    			myUnit = itr.next();
		    			if(myUnit.getPosition().getApproxDistance(center) >= 25*32 && squad.getUnitCount() > 1)
		    				freeAgents.takeUnit(myUnit, itr);
		    		}
		    		
		    		center = squad.findCenter();
//		    		System.out.println(squad.getUnits().size());
		    		//merge small squads together
		    		for(Squad otherSquad: army) {
		    			if(otherSquad.getUnits().size() > 0 && !otherSquad.equals(squad) 
		    				&& otherSquad.getUnits().size() + squad.getUnits().size() <= 12 
		    				&& center.getApproxDistance(otherSquad.findCenter()) < 4*32
		    				&& otherSquad.getObjective().equals(squad.getObjective())) {
		    				
		    				squad.takeAllUnits(otherSquad);
		    			}
		    		}
		    		
		    		//send commands
		    		if(squad.isTogether() || squad.getCommand() == UnitState.ATTACKING) {
			    		squad.attack(range);
		    		} else {
		    			squad.groupUp();
		    		}
	
		    	} else if(defenseChoke != null ) {
		    		allAttacking = false;
	//	    		if(squad.getUnits().size() < 12)
	    			squad.takeAllUnits(freeAgents);
		    		squad.holdChoke(defenseChoke);
		    	}
    		} else {
    			squadItr.remove();
    		}
    	}
    	if(allAttacking) {
    		Squad tempSquad = new Squad(game);
			tempSquad.takeAllUnits(freeAgents);
			army.add(tempSquad);
	    	if(defenseChoke != null) {
				tempSquad.holdChoke(defenseChoke);
	    	}
    	}        	
    	if(defenseChoke != null) {
			freeAgents.holdChoke(defenseChoke);
    	}
 
    	//shuttle shenangigans
    	for(int i: myUnits.keySet()) {
    		MyUnit myUnit = myUnits.get(i);
    		if(myUnit instanceof Reaver) {
    			myUnit.attack(attackPosition, false);
    			if(self.minerals() >=25) {//canAfford(UnitType.Protoss_Scarab)) {
    				myUnit.getUnit().train(UnitType.Protoss_Scarab);
    			}
    		}
    	}
    	for(int i: myUnits.keySet()) {
    		MyUnit myUnit = myUnits.get(i);
    		if(myUnit instanceof Shuttle) {
    			if(aBaseIsUnderAttack)
    				myUnit.commandShuttle(null);
    			else
    				myUnit.commandShuttle(dropBase);
    		}
    	}
    	Unit closestGroundSquishy;
    	Unit closestGroundEnemy;
    	Unit closestCloaked;
    	boolean canStorm = false;
    	Unit weakestAirEnemy;
    	int myRange;
    	int hisRange = 0;
    	double himToMeX = 0;
    	double himToMeY = 0;
    	double hisSpeedTowardsMe = 0;
    	double mySpeedTowardsHim = 0;
    	for(Unit myUnit:self.getUnits()) {
    		closestEnemy = null;
    		closestSquishy = null;
    		closestGroundSquishy = null;
    		closestGroundEnemy = null;
        	closestCloaked = null;
        	weakestAirEnemy = null;
    		if(myUnit.isCompleted() && !myUnit.getType().isBuilding() && !myUnit.getType().isWorker() && myUnit.getType() != UnitType.Protoss_Shuttle
    			&& myUnit.getType() != UnitType.Protoss_Zealot && myUnit.getType() != UnitType.Protoss_Dragoon
    			&& myUnit.getType() != UnitType.Protoss_High_Templar && myUnit.getType() != UnitType.Protoss_Archon && myUnit.getType() != UnitType.Protoss_Reaver) {
				for(Unit hisUnit: myUnit.getUnitsInRadius(myUnit.getType().sightRange())) { 				
					if(hisUnit.getPlayer().equals(game.enemy()) && hisUnit.isVisible(self) && hisUnit.isDetected() && !hisUnit.isInvincible() 					
						&& (!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)
						&& hisUnit.getType() != UnitType.Zerg_Larva && hisUnit.getType() != UnitType.Zerg_Egg) {
						
						if(closestEnemy == null ||	myUnit.getPosition().getApproxDistance(hisUnit.getPosition()) < myUnit.getPosition().getApproxDistance(closestEnemy.getPosition())) {  						
							closestEnemy = hisUnit;
						}
						
						if(!hisUnit.getType().isBuilding())
							canStorm = true;
						
						if(!hisUnit.isFlying() && (closestGroundSquishy == null
							|| myUnit.getPosition().getApproxDistance(hisUnit.getPosition()) < myUnit.getPosition().getApproxDistance(closestGroundSquishy.getPosition()))) {
							
							closestGroundSquishy = hisUnit;
						}
					}    				
				}	
				closestSquishy = closestEnemy;
				for(Unit hisUnit: myUnit.getUnitsInRadius(myUnit.getType().sightRange()+32)) {    				
					if(hisUnit.getPlayer().equals(game.enemy()) && hisUnit.isVisible(self) && hisUnit.isDetected()
						&& hisUnit.getType() != UnitType.Zerg_Larva && hisUnit.getType() != UnitType.Zerg_Egg) {
//							&& hisUnit.getType().isBuilding() && !hisUnit.getType().canAttack() && hisUnit.getType() != UnitType.Terran_Bunker) {
						
						if(closestSquishy == null && (closestEnemy == null 
							|| myUnit.getPosition().getApproxDistance(hisUnit.getPosition()) < myUnit.getPosition().getApproxDistance(closestEnemy.getPosition()))) {    					
							closestEnemy = hisUnit;
						}
						if(hisUnit.isFlying()
							&& (weakestAirEnemy == null || hisUnit.getHitPoints() + hisUnit.getShields() < weakestAirEnemy.getHitPoints() + weakestAirEnemy.getShields() ||
							(hisUnit.getHitPoints() + hisUnit.getShields() == weakestAirEnemy.getHitPoints() + weakestAirEnemy.getShields()
							&& myUnit.getPosition().getApproxDistance(hisUnit.getPosition()) < myUnit.getPosition().getApproxDistance(weakestAirEnemy.getPosition())))) {  
							
							weakestAirEnemy = hisUnit;
						}
						
						if(!hisUnit.isFlying() 
							&& (closestGroundEnemy == null ||myUnit.getPosition().getApproxDistance(hisUnit.getPosition()) < myUnit.getPosition().getApproxDistance(closestGroundEnemy.getPosition()))) {  
							
							closestGroundEnemy = hisUnit;
						}
					}    				
				}

				myRange = self.weaponMaxRange(myUnit.getType().groundWeapon());
				if(closestEnemy != null) {			    	
			    	hisRange = game.enemy().weaponMaxRange(closestEnemy.getType().groundWeapon());
			    	if(closestEnemy.getDistance(myUnit) == 0) {
			    		hisSpeedTowardsMe = Math.sqrt(Math.pow(closestEnemy.getVelocityX(),2) + Math.pow(closestEnemy.getVelocityY(),2));
			    	} else {
				    	himToMeX = (myUnit.getX() - closestEnemy.getX())/closestEnemy.getDistance(myUnit);
				    	himToMeY = (myUnit.getY() - closestEnemy.getY())/closestEnemy.getDistance(myUnit);
				    	hisSpeedTowardsMe = closestEnemy.getVelocityX()*himToMeX + closestEnemy.getVelocityY()*himToMeY;
				    	mySpeedTowardsHim = -myUnit.getVelocityX()*himToMeX - myUnit.getVelocityY()*himToMeY;
			    	}
				}
				
	    		if(myUnit.getType() == UnitType.Protoss_Shuttle) {  
	    			boolean gotCommand = false;
	    			Unit closestPassenger = null;
	    			Unit followUnit = null;
 		    				
    				//loaded	    		
    				if(myUnit.canUnload()) {		    
		    			if(closestSquishy != null && !closestSquishy.getType().groundWeapon().equals(WeaponType.None)
			    			&& closestSquishy.getDistance(myUnit) <= game.enemy().weaponMaxRange(closestSquishy.getType().groundWeapon())) {
			    				
			    			//don't drop in a dangerous area
			    			myUnit.move(self.getStartLocation().toPosition());
			    			gotCommand = true;
		    			} else {
		    				for(Unit u: myUnit.getLoadedUnits()) {	
		    					if(u.getType() == UnitType.Protoss_Reaver && u.getScarabCount() > 0 
		    						&& closestGroundSquishy != null		
		    						&& BWTA.getRegion(myUnit.getPosition()) != null			
//		    			    		&& BWTA.getRegion(myUnit.getPosition()).equals(BWTA.getRegion(closestGroundSquishy.getPosition()))
		    						&& closestGroundSquishy.hasPath(u.getPosition())
		    			    		&& BWTA.getGroundDistance(u.getTilePosition(), closestGroundSquishy.getTilePosition()) <= 8*32
		    			    		) { 
		    						
		    						if(myUnit.getDistance(closestGroundSquishy) <= 8*32)
		    							myUnit.unload(u);
		    						else 
		    							myUnit.move(self.getStartLocation().toPosition());
		    						gotCommand = true;
		    					} else if (u.getType() == UnitType.Protoss_High_Templar && u.getEnergy() >= 75 
		    						&& self.hasResearched(TechType.Psionic_Storm) && closestGroundSquishy != null) {
		    						myUnit.unload(u);
		    						gotCommand = true;
		    					} else if (u.getType() == UnitType.Protoss_Dragoon && u.getGroundWeaponCooldown() == 0) {
		    						if(closestSquishy == null 
		    							|| !closestSquishy.isInWeaponRange(myUnit)
		    							|| myUnit.getPosition().getApproxDistance(self.getStartLocation().toPosition()) < 10)
		    							myUnit.unload(u);
		    						else 
		    							myUnit.move(self.getStartLocation().toPosition());
		    						gotCommand = true;
		    					}
		    				}
		    			}
	    			}
//	    				if(self.completedUnitCount(UnitType.Protoss_Zealot) > 0) {
//	    					Unit closestZealot = null;
//	    					for(Unit u: self.getUnits()) {
//	    						if(u.getType() == UnitType.Protoss_Zealot && (closestZealot == null 
//	    							|| u.getDistance(myUnit.getPosition()) < closestZealot.getDistance(myUnit.getPosition()))) {
//	    							
//	    							myUnit.load(closestZealot);
//	    						}		    							
//	    					}	
//	    				}
    				if(!gotCommand && self.allUnitCount(UnitType.Protoss_Reaver) > 0 
    					&& myUnit.getSpaceRemaining() >= UnitType.Protoss_Reaver.spaceRequired()) {
    				//empty	
    					closestPassenger = null;
    					followUnit = null;
    					boolean hasReaver = false;
    					for(Unit u: self.getUnits()) {
    						if(u.getType() == UnitType.Protoss_Reaver) {
    							if(!u.isLoaded()) {
	    							if (closestGroundSquishy == null && u.getTrainingQueue().size() + u.getScarabCount() == 5
	    								|| closestGroundSquishy != null && (u.getTrainingQueue().size() == 1 && u.getRemainingTrainTime() > 90 
	    								|| u.getTrainingQueue().size() > 1)) {		
	    								if(closestPassenger == null || u.getDistance(myUnit) < closestPassenger.getDistance(myUnit)) {
	    									closestPassenger = u;		    							
	    								}
		    						} else if(u.getRemainingBuildTime()*self.topSpeed(myUnit.getType()) 
		    							< myUnit.getPosition().getApproxDistance(u.getPosition())
		    							&& (followUnit == null || u.getDistance(myUnit) < followUnit.getDistance(myUnit))) {
		    							
		    							//move to reavers that are almost done
										followUnit = u;		    							
									}	   
    							} else if(u.getTransport().getID() == myUnit.getID()) {
    								hasReaver = true;
    							}
    						}		    							
    					}	
    					if(closestPassenger != null 
    						&& (!hasReaver || closestPassenger.getPosition().getApproxDistance(myUnit.getPosition()) < 5*32)) {
    						
    						myUnit.rightClick(closestPassenger);
    						gotCommand = true;
    					} else if(followUnit != null 
    						&& (!hasReaver || followUnit.getPosition().getApproxDistance(myUnit.getPosition()) < 5*32)) {
    						
    						myUnit.move(followUnit.getPosition());
    						gotCommand = true;
    					}
    				} 
    				if(!gotCommand && self.completedUnitCount(UnitType.Protoss_High_Templar) > 0 
    					&& myUnit.getSpaceRemaining() >= UnitType.Protoss_High_Templar.spaceRequired()) {
    				//empty	
    					closestPassenger = null;
    					followUnit = null;
    					for(Unit u: self.getUnits()) {
    						if(u.getType() == UnitType.Protoss_High_Templar && u.isCompleted() && !u.isLoaded()
    							&& u.getDistance(myUnit) < self.sightRange(myUnit.getType())) {
    							
    							if (u.getSpellCooldown() == 0 && u.getLastCommand().getUnitCommandType() != UnitCommandType.Use_Tech) {		
    								if(closestPassenger == null || u.getDistance(myUnit) < closestPassenger.getDistance(myUnit)) {
    									closestPassenger = u;		    							
    								}
	    						} else if(followUnit == null || u.getDistance(myUnit) < followUnit.getDistance(myUnit)) {
									followUnit = u;		    							
								}	 
    							
    						}		    							
    					}		
    					if(closestPassenger != null) {
    						myUnit.rightClick(closestPassenger);
    						gotCommand = true;
    					} else if(followUnit != null) {
    						myUnit.move(followUnit.getPosition());
    						gotCommand = true;
    					}
    				} 
    				if(!gotCommand && self.completedUnitCount(UnitType.Protoss_Dragoon) > 0 
    					&& myUnit.getSpaceRemaining() >= UnitType.Protoss_Dragoon.spaceRequired()) {
    				//empty	
    					closestPassenger = null;
    					followUnit = null;
    					for(Unit u: self.getUnits()) {
    						if(u.getType() == UnitType.Protoss_Dragoon && u.isCompleted() && !u.isLoaded()) {
    							if (u.isUnderAttack() && u.getShields() + u.getHitPoints() <= 50) {		
    								if(closestPassenger == null || u.getDistance(myUnit) < closestPassenger.getDistance(myUnit)) {
    									closestPassenger = u;		    							
    								}
	    						} else if(followUnit == null || u.getDistance(attackPosition) 
	    							< followUnit.getDistance(attackPosition)) {
									followUnit = u;		    							
								}	 
    							
    						}		    							
    					}		
    					if(closestPassenger != null) {
    						myUnit.rightClick(closestPassenger);
    						gotCommand = true;
    					} else if(followUnit != null) {
    						myUnit.move(followUnit.getPosition());
    						gotCommand = true;
    					}
    				}
    				
    				if(!gotCommand && myUnit.getLoadedUnits().size() == 0) {
    					myUnit.move(self.getStartLocation().toPosition());
    					gotCommand = true;
    				}
	    			
	    			if (!gotCommand) {		   			
		    			if(myUnit.isUnderAttack() || (closestSquishy != null && !closestSquishy.getType().airWeapon().equals(WeaponType.None)
		    				&& closestSquishy.getDistance(myUnit) <= game.enemy().weaponMaxRange(closestSquishy.getType().airWeapon()) + 20)) {
		    				
		    				//safety first
		    				myUnit.move(self.getStartLocation().toPosition());
		    				gotCommand = true;
		    			} else {
		    				myUnit.move(attackPosition);
		    				gotCommand = true;
		    			}
	    			}	
	    			//abm	 
//    				if(myUnit.getVelocityX() == 0 && myUnit.getVelocityY() == 0) {
//    					myUnit.move(new Position(myUnit.getX() + (int)(12*(Math.random()- 0.5)),myUnit.getY()));
//    				}		    		    			   			
	    		} else if(myUnit.getType() == UnitType.Protoss_Observer) {  	
	    			scout(myUnit);
	    		} else if(myUnit.getType() == UnitType.Protoss_Corsair) {  	    			
	    			scout(myUnit);
	    		}
    		}
    	}    	
    }
    
//    /** Returns whether a goon can kite a certain unit.
//     * If a unit is not kiteable or can't attack, it means you will attack on cooldown.
//     * TODO: units with acceleration
//     */
//    public boolean canKite(Unit myUnit, Unit hisUnit) {
//    	boolean kiteable = true;
//    	int myRange = self.weaponMaxRange(myUnit.getType().groundWeapon());
//    	int hisRange = game.enemy().weaponMaxRange(hisUnit.getType().groundWeapon());
//    	double himToMeX = (myUnit.getX() - hisUnit.getX())/hisUnit.getDistance(myUnit);
//    	double himToMeY = (myUnit.getY() - hisUnit.getY())/hisUnit.getDistance(myUnit);
//    	double hisSpeedTowardsMe = hisUnit.getX()*himToMeX + hisUnit.getY()*himToMeY;
//    	kiteable &= myRange - 6*(myUnit.getType().topSpeed() - hisSpeedTowardsMe) > hisRange;
//    	kiteable |= !hisUnit.canAttackUnit(myUnit);
//    	return kiteable;
//    }
//    
    /** Decide where the next buildings should go
     * 
     */
    public void updateBuildingMap(UnitType nextItem) throws Exception {
    	Position myBase = new Position(self.getStartLocation().toPosition().getX() + 2*PIXELS_PER_TILE,
    								self.getStartLocation().toPosition().getY() + (int) 1.5*PIXELS_PER_TILE);
    	TilePosition baseTile = self.getStartLocation();
    	Position mainChoke = BWTA.getNearestChokepoint(myBase).getCenter();
    	TilePosition bestTile = null;
    	TilePosition nextTile = null;
    	TilePosition baseLocation = self.getStartLocation();
    	TilePosition backupTile = null;
    	double closestDistance = 99999;
    	double d;
		Position nextPosition = null;
		Position buildingCenter = null;	
		Position bestPosition = null;
		Unit builderProbe = null;
    	
    	//special bypass for FFE
		if(enemyRace == Race.Zerg && BOProgress < 8 && (nextItem != UnitType.Protoss_Pylon || BOProgress ==1)
			&& nextItem != UnitType.Protoss_Nexus && naturalChoke != null) {
			
			TilePosition p1 = naturalChoke.getSides().first.toTilePosition();
			TilePosition p2 = naturalChoke.getSides().second.toTilePosition();
			if(nextItem == UnitType.Protoss_Photon_Cannon) {
		    	for(int x = Math.min(p1.getX(), p2.getX()) - nextItem.tileWidth() -3; x <= Math.max(p1.getX(), p2.getX()) +3; x += 1) {
		        	for(int y = Math.min(p1.getY(), p2.getY()) -nextItem.tileHeight() -3; y <= Math.max(p1.getY(), p2.getY()) +3; y += 1) {
		        		nextTile = new TilePosition(x,y);		        		
		        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
		        									, nextTile.toPosition().getY() + nextItem.tileHeight()*16);		        		
		        		
	            		if(shouldBuildHere(nextItem, nextTile, mainChoke, bestPosition, false)) {
//	            			game.drawCircleMap(nextTile.toPosition(), 3, Color.Green);
		            		bestTile = nextTile;
		        			bestPosition = buildingCenter;
	            		}
		        	}
		    	}
			} else if (nextItem == UnitType.Protoss_Pylon) {
		    	for(int x = Math.max(p1.getX(), p2.getX()) -nextItem.tileWidth() -6; x <= Math.min(p1.getX(), p2.getX()) +6; x += 1) {
		        	for(int y = Math.max(p1.getY(), p2.getY()) -nextItem.tileHeight() -3; y <= Math.min(p1.getY(), p2.getY()) +4; y += 1) {
		        		nextTile = new TilePosition(x,y);
		        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
													, nextTile.toPosition().getY() + nextItem.tileHeight()*16);				        			  
		            	
		            	if((int) Math.abs(p2.getX() - p1.getX()) > 10 || (int) Math.abs(p2.getY() - p1.getY()) > 6) {
		            		//use pylon as part of the wall-off if the choke is long
		            		if(shouldBuildHere(nextItem, nextTile, mainChoke, bestPosition, false)) {
//		            			game.drawCircleMap(nextTile.toPosition(), 3, Color.Green);
			            		bestTile = nextTile;
			        			bestPosition = buildingCenter;
		            		}
		            	} else {
		            		// build pylon farther back if choke is short
		            		if(shouldBuildHere(nextItem, nextTile, natural.getPosition(), bestPosition, false)) {
//		            			game.drawCircleMap(nextTile.toPosition(), 3, Color.Green);
			            		bestTile = nextTile;
			        			bestPosition = buildingCenter;
		            		}
		            	}
		        	}
		    	}
			} else {
				//forge and gateway
		    	for(int x = Math.min(p1.getX(), p2.getX()) -nextItem.tileWidth(); x <= Math.max(p1.getX(), p2.getX()) +1; x += 1) {
		        	for(int y = Math.min(p1.getY(), p2.getY()) -nextItem.tileHeight(); y <= Math.max(p1.getY(), p2.getY()) +1; y += 1) {
		        		nextTile = new TilePosition(x,y);
		        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
								, nextTile.toPosition().getY() + nextItem.tileHeight()*16);		
		        		
		        		if(intersects(nextItem, buildingCenter, naturalChoke.getSides().first, naturalChoke.getSides().second)) {				        			
		            		if(shouldBuildHere(nextItem, nextTile, mainChoke, bestPosition, false)) {
//		            			game.drawCircleMap(nextTile.toPosition(), 3, Color.Green);
			            		bestTile = nextTile;
			        			bestPosition = buildingCenter;
		            		}
		        		}
		        	}
		    	}
			}
		}
		
		if(bestTile == null) {
	    	if(nextItem == UnitType.Protoss_Pylon) {
		    	for(int x = baseTile.getX() - 2; x <= baseTile.getX() + 4; x +=6) {
		        	for(int y = baseTile.getY() - 2; y <= baseTile.getY() + 4; y +=2) {	
		        		nextTile = new TilePosition(x,y);
		        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
								, nextTile.toPosition().getY() + nextItem.tileHeight()*16);	
		        		
	            		if(shouldBuildHere(nextItem, nextTile, mainChoke, bestPosition, false)) {
		            		bestTile = nextTile;
		        			bestPosition = buildingCenter;
	            		}
		        	}
		    	}	    	
		    	if(bestTile == null) {	   
		    		//build a spotter pylon
			    	for(int x = baseTile.getX() - 41 + pylonSkip; x <= baseTile.getX() + 43; x += 5) {
			        	for(int y = baseTile.getY() - 45; y <= baseTile.getY() + 43; y += 6) {	
			        		
			        		nextTile = new TilePosition(x,y);
			        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
									, nextTile.toPosition().getY() + nextItem.tileHeight()*16);	
				    		if(BWTA.getRegion(baseTile).getPolygon().isInside(buildingCenter)
				    			&& shouldBuildHere(nextItem, nextTile, baseTile.toPosition(), bestPosition, true)) {		
				    			
				    			bestTile = nextTile;
				    			bestPosition = buildingCenter;
				    		}
			        	}
		    		}
			    	if(bestTile == null)
			    		pylonSkip = 2;
		    	}
	    	} else if (nextItem == UnitType.Protoss_Assimilator) {
	    		//can be optimized
	            for (Unit neutralUnit : game.neutral().getUnits()) {            	
	                if (neutralUnit.getType() == UnitType.Resource_Vespene_Geyser) {
	                	boolean needsGas = false;
	                	for(Unit u: game.getUnitsOnTile(BWTA.getNearestBaseLocation(neutralUnit.getTilePosition()).getTilePosition())) {
	                		if(u.getType().isResourceDepot() && u.getPlayer().equals(self) && u.isCompleted()) {
	                			needsGas = true;
	                		}
	                	}
	                	if(needsGas && shouldBuildHere(nextItem, neutralUnit.getTilePosition(), baseLocation.toPosition(), bestPosition, false)) {
//	                		&& (bestTile == null || baseLocation.getDistance(neutralUnit.getTilePosition()) < baseLocation.getDistance(bestTile))) {
	                		bestTile = neutralUnit.getTilePosition();
	                		bestPosition = neutralUnit.getPosition();
	                	}
	                }
	            } 
	    	} else if (nextItem == UnitType.Protoss_Nexus) {  		
	    		boolean baseTaken;
	    		double bestBasePriority = 999;
	    		double basePriority = 999;
	            for (BaseLocation b : BWTA.getBaseLocations()) {  
	            	baseTaken = false;
	            	TilePosition t = b.getTilePosition();
	            	for(Unit u: game.getUnitsInRectangle(t.toPosition(), new Position((t.getX()+4)*32, (t.getY()+3)*32))) {
	            		if(u.getType().isResourceDepot()) {
	            			baseTaken = true;
	            		}
	            	}
	            	//don't expo to an enemy base
	            	HisUnit hisUnit = null;
	            	for(int i: enemyBuildings.keySet()) {
	            		hisUnit = enemyBuildings.get(i);
	            		if(hisUnit.getUnit().exists() && hisUnit.getType().isResourceDepot() 
	            			&& BWTA.getRegion(hisUnit.getPosition()).equals(b.getRegion())) {	     
	            			baseTaken = true;
	            		}
	            	}
	            	//metric of which base to take
	            	if(enemyMain != null) 
		            	basePriority = safety(b.getPosition());
	            		
	        		if(!b.isIsland()  && !baseTaken) {//&& !b.isMineralOnly()
	        			if(self.allUnitCount(UnitType.Protoss_Nexus) == 1 || enemyMain == null) {
	        				//find natural base
	        				bestTile = natural.getTilePosition();
	        			} else {
	        				//find bases after natural
	        				if(bestTile == null || basePriority > bestBasePriority) {
	        					bestBasePriority = basePriority;
	        					bestTile = t;
	        				}
	        			}
	            	}
	            }   
	    	} else if (nextItem == UnitType.Protoss_Photon_Cannon) {  
		    	for(int x = baseTile.getX() - 2; x <= baseTile.getX() + 4; x +=2) {
		        	for(int y = baseTile.getY() - 2; y <= baseTile.getY() + 3; y +=5) {	
		        		nextTile = new TilePosition(x,y);
		        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
								, nextTile.toPosition().getY() + nextItem.tileHeight()*16);	
		        		
	            		if(shouldBuildHere(nextItem, nextTile, mainChoke, bestPosition, false)) {
		            		bestTile = nextTile;
		        			bestPosition = buildingCenter;
	            		}
		        	}
		    	}	
	    	} else {
	    		int xAdjust = 0;
	    		//all other buildings
		    	for(int x = baseTile.getX() - 6; x <= baseTile.getX() + 8; x +=4) {
		        	for(int y = baseTile.getY() - 6; y <= baseTile.getY() + 8; y +=3) {	      
		        		xAdjust = x;
		        		if(x < baseTile.getX() - 2)
		        			xAdjust = x + 4-nextItem.tileWidth();
		        		//don't place big buildings right next to nexus
		        		if(!(Math.abs(x-baseTile.getX()) <= 4 && Math.abs(y-baseTile.getY()) <= 3)) {
			        		nextTile = new TilePosition(xAdjust,y);
			        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
									, nextTile.toPosition().getY() + nextItem.tileHeight()*16);	
			        		
		            		if(shouldBuildHere(nextItem, nextTile, mainChoke, bestPosition, false)) {
			            		bestTile = nextTile;
			        			bestPosition = buildingCenter;
		            		}
		        		}
		        	}
		    	}
		    	if(bestTile == null) {
		    		//build a building close to nexus
			    	for(int x = baseTile.getX() - 41; x <= baseTile.getX() + 43; x += 5) {
			        	for(int y = baseTile.getY() - 49; y <= baseTile.getY() + 45; y += 6) {	 
			        		if(!(Math.abs(x-baseTile.getX()) <= 4 && Math.abs(y-baseTile.getY()) <= 3)) {
				        		nextTile = new TilePosition(x,y);
				        		nextPosition = nextTile.toPosition();
				        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
										, nextTile.toPosition().getY() + nextItem.tileHeight()*16);	
					    		if(BWTA.getRegion(baseTile).getPolygon().isInside(nextPosition)) {					        		
				            		if(shouldBuildHere(nextItem, nextTile, baseTile.toPosition(), bestPosition, false)) {
					            		bestTile = nextTile;
					        			bestPosition = buildingCenter;
				            		}
					    		}
			        		}
			        	}
		    		}
		    	}
	    	}
		}
		
    	if(bestTile != null) {
	    	nextItemX = bestTile.getX();
	    	nextItemY = bestTile.getY();
    	} else if(nextItem != UnitType.Protoss_Nexus && nextItem != UnitType.Protoss_Assimilator) {
    		backupTile = getBackupTile(baseTile, nextItem);
    		if(backupTile != null) {    			
	    		nextItemX = backupTile.getX();
	    		nextItemY = backupTile.getY();
    		}
    	}
    	game.drawBoxMap(nextItemX*PIXELS_PER_TILE, nextItemY*PIXELS_PER_TILE, (nextItemX+nextItem.tileWidth())*PIXELS_PER_TILE,
    			(nextItemY+nextItem.tileHeight())*PIXELS_PER_TILE, new Color(0,255,255));
    }
    
    public double safety(Position pos) {
    	if(enemyMain != null)
    		return safety(pos, self.getStartLocation().toPosition(), enemyMain.toPosition());
    	else
    		return safety(pos, self.getStartLocation().toPosition(), null);
    }
    
    public double safety(Position pos, Position source, Position sink) {
    	if(sink != null)
	    	return 1.0/Math.max(1, pos.getApproxDistance(source))
	    		   -1.0/Math.max(1, pos.getApproxDistance(sink));
    	else 
    		return 1.0/Math.max(1, pos.getApproxDistance(source));
    }    
    
    /** Enhances the game.canBuildHere method, ignoring the building probe for collision and leaving space for expos
     * 
     * @param myUnit
     * @param nextTile
     * @param homingPoint
     * @param bestPoint
     * @param repulse		false
     * @return
     */
    public boolean shouldBuildHere(UnitType myUnit, TilePosition nextTile, Position homingPoint, Position bestPoint, boolean repulse) {
		Unit builderProbe = null;
    	Position buildingCenter = new Position(nextTile.toPosition().getX() + myUnit.tileWidth()*16
				, nextTile.toPosition().getY() + myUnit.tileHeight()*16);		  
    	
    	//check for optimal placement
    	if(repulse) {
	    	if(bestPoint != null && homingPoint.getApproxDistance(buildingCenter) <= homingPoint.getApproxDistance(bestPoint))
	    		return false;  
    	} else {
	    	if(bestPoint != null && homingPoint.getApproxDistance(buildingCenter) >= homingPoint.getApproxDistance(bestPoint))
	    		return false;    	
    	}
    	
		int blockingUnits = 0;
    	for(Unit u: game.getUnitsInRectangle(nextTile.toPosition(), 
    		new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*32, nextTile.toPosition().getY() + nextItem.tileHeight()*32))) {
    		
    		if(u.getPlayer() == self && u.getType() == UnitType.Protoss_Probe) {
    			if (builderProbe == null) {
    				builderProbe = u;
    			} else if(u.getPosition().getApproxDistance(nextTile.toPosition()) 
    				< builderProbe.getPosition().getApproxDistance(nextTile.toPosition())) { 
    				builderProbe = u;	    
    				blockingUnits++;
				} else {
					blockingUnits++;
				}
    		//you're allowed to build assimilators on geysers
    		} else if(!(u.getType() == UnitType.Resource_Vespene_Geyser && myUnit == UnitType.Protoss_Assimilator)){
    			blockingUnits++;
    		}		            		
    	}	
    	
    	//check for blocking Units 
    	if(blockingUnits > 0)
    		return false;
    	
    	if(builderProbe == null) {
    		if(!game.canBuildHere(nextTile, myUnit))
    			return false;
    	} else {
    		if(!game.canBuildHere(nextTile, myUnit, builderProbe))
    			return false;
    	}
    	//check for blocking future expos
    	TilePosition baseTile = null;
    	for(BaseLocation base: BWTA.getBaseLocations()) {
    		baseTile = base.getTilePosition();
    		if(baseTile.getX() < nextTile.getX() + myUnit.tileWidth() && baseTile.getX()+4 > nextTile.getX()
    			&& baseTile.getY() < nextTile.getY() + myUnit.tileHeight() && baseTile.getY()+3 > nextTile.getY()) {
    			return false;
    		}
    	}
    	
		//don't build in places that block mining
    	if(myUnit != UnitType.Protoss_Assimilator) {
			for(Unit u: game.getUnitsInRectangle(
				nextTile.toPosition().getX()-2*PIXELS_PER_TILE, nextTile.toPosition().getY()-2*PIXELS_PER_TILE,
				nextTile.toPosition().getX()+3*PIXELS_PER_TILE, nextTile.toPosition().getY()+3*PIXELS_PER_TILE)) {
				
				if(u.getType().isResourceContainer() || u.getType().isRefinery()) {
					return false;
				}
			}
    	}
    	
    	return true;
    }
    
    /** Uses cross products to determine whether a building touches a line on the map
     * 
     */
    public static boolean intersects (UnitType building, Position buildingPos, Position a, Position b) {
    	int bx = buildingPos.getX();
    	int by = buildingPos.getY();
    	int[][] corners = {{bx-building.width()/2, by-building.height()/2}, {bx-building.width()/2, by+building.height()/2}, 
    						{bx+building.width()/2, by+building.height()/2}, {bx+building.width()/2, by-building.height()/2}};
    	
    	//check if outside the line entirely
    	if(bx > Math.max(a.getX(), b.getX()) || bx + building.width() < Math.min(a.getX(), b.getX())
    		|| by > Math.max(a.getY(), b.getY()) || by + building.height() < Math.min(a.getY(), b.getY())) {
    			return false;
    	}
    	
    	int ux = b.getX() - a.getX();
    	int uy = b.getY() - a.getY();
    	float sign = -2f;
    	float tempSign;
    	int vx = 0;
    	int vy = 0;
    	for(int i = 0; i<4 ;i++) {
    		vx = corners[i][0] - a.getX();
    		vy = corners[i][1] - a.getY();
    		tempSign = Math.signum(ux*vy - uy*vx);
    		if(sign == -2f) {
    			sign = tempSign;
    		} else if(sign != tempSign && tempSign != 0f) {
    			return true;
    		}
    	}
    	return false;    	
    }
    
    /** Finds an acceptable tile if the good ones are already taken. This method may cause units to clog.
     *  Returns the TilePosition of an acceptable tile.
     */
    public TilePosition getBackupTile(TilePosition baseTile, UnitType nextItem) {
    	TilePosition nextTile = null;
    	TilePosition pylonTile = null;
    	TilePosition backupTile = null;
		Position buildingCenter = null;
		Position bestPosition = null;
		if(nextItem == UnitType.Protoss_Pylon || nextItem == UnitType.Protoss_Nexus) {
			for(int x = baseTile.getX() - 10; x <= baseTile.getX() + 10; x +=1) {
	        	for(int y = baseTile.getY() - 10; y <= baseTile.getY() + 10; y +=1) {	  
	        		nextTile = new TilePosition(x,y);
	        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
							, nextTile.toPosition().getY() + nextItem.tileHeight()*16);	
	        		if(shouldBuildHere(nextItem, nextTile, baseTile.toPosition(), bestPosition, false)) {
	            		backupTile = nextTile;
	        			bestPosition = buildingCenter;
	        		}
	        	}
	    	}
		}
		//search power fields to place other buildings
		for(Unit myUnit: self.getUnits()) {
			if(myUnit.getType() == UnitType.Protoss_Pylon) {
				pylonTile = myUnit.getTilePosition();
				for(int x = pylonTile.getX() - 7; x <= pylonTile.getX() + 9; x +=1) {
		        	for(int y = pylonTile.getY() - 4; y <= pylonTile.getY() + 6; y +=1) {	  
		        		nextTile = new TilePosition(x,y);
		        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
								, nextTile.toPosition().getY() + nextItem.tileHeight()*16);	
		        		if(shouldBuildHere(nextItem, nextTile, baseTile.toPosition(), bestPosition, false)) {
		            		backupTile = nextTile;
		        			bestPosition = buildingCenter;
		        		}
		        	}
		    	}
			}
		}

    	return backupTile;
    }
    
    /**build a building at the optimal time
     * returns true if build command was issued, else returns false 
    */
    public boolean buildBuilding(UnitType building, int x, int y) throws NullPointerException {   
    	//decide where to put the building    	
    	TilePosition buildingLoc = new TilePosition(x,y);
    	Position buildingCenter = new Position(buildingLoc.toPosition().getX() + building.tileWidth()*16
				, buildingLoc.toPosition().getY() + building.tileHeight()*16);		  
    	
    	//find the worker to build with
    	double shortestDist = 999999;
    	Unit closestWorker = null;
    	UnitCommand command = null;
    	boolean match = false;
    	double dist;
    	for (Unit myUnit : self.getUnits()) {
            //build buildings at the optimal time
            if (myUnit.getType().isWorker() && !myUnit.isGatheringGas() && !match && myUnit.isCompleted() && !myUnit.equals(scoutingProbe)
            	&& myUnit.hasPath(buildingLoc.toPosition())) {// &&
//            	(game.canBuildHere(buildingLoc, building, myUnit, true) || !game.isVisible(buildingLoc))) {
            	
            	command = myUnit.getLastCommand();            
            	dist = myUnit.getPosition().getApproxDistance(buildingLoc.toPosition());
            	if(myUnit.getTargetPosition().equals(buildingCenter)) {
            		//prevent multiple workers from building the same thing
            		closestWorker = myUnit;
            		shortestDist = dist;
            		match = true;
            	} else if (myUnit.getType().isWorker() && !myUnit.isGatheringGas() && myUnit.isCompleted() && !myUnit.equals(scoutingProbe)) {// &&
	//            	(game.canBuildHere(buildingLoc, building, myUnit, true) || !game.isVisible(buildingLoc))) {
	            	
	            	command = myUnit.getLastCommand();            
	            	dist = myUnit.getPosition().getApproxDistance(buildingLoc.toPosition());
	            	if (dist < shortestDist) {
	            		closestWorker = myUnit;
	            		shortestDist = dist;
	            	}
	            }
            }
        }
    	   	
    	//double t = timeToMove(UnitType.Protoss_Probe, shortestDist);
    	//int mineralsWhileMoving = (int) (shortestDist/t * (mineralsPerFrame - 0.0474));
    	//int gasWhileMoving = (int) (shortestDist/t * gasPerFrame);
    	int mineralsWhileMoving = (int) (shortestDist/UnitType.Protoss_Probe.topSpeed() * (mineralsPerFrame - 0.0474));
    	int gasWhileMoving = (int) (shortestDist/UnitType.Protoss_Probe.topSpeed() * gasPerFrame);
    	
    	//decide whether to build the building
    	if (closestWorker != null && canAfford(building, mineralsWhileMoving, gasWhileMoving)) {
    		boolean fullyExplored = true;
    		for(x=0; x<building.tileWidth();x++) {
        		for(y=0; y<building.tileHeight();y++) {
        			if(!game.isExplored(new TilePosition(buildingLoc.getX()+x, buildingLoc.getY()+y)))
        				fullyExplored = false;
        		}
    		}    			
    		if(closestWorker.getLastCommand().isQueued())
				closestWorker.move(buildingLoc.toPosition(), false); 
    		if(game.canBuildHere(buildingLoc, building, closestWorker, true) && fullyExplored //&& !closestWorker.getLastCommand().isQueued()
    			&& self.minerals() >= building.mineralPrice() && self.gas() >= building.gasPrice()) {    

    			closestWorker.build(building, buildingLoc);
//    			reservedMinerals += building.mineralPrice();
//    			reservedGas += building.gasPrice();
    		} else {
    			closestWorker.move(buildingCenter, false);    			
    		}
    		game.drawTextMap(closestWorker.getPosition(), "builder");
    		return true;
    	} else {
    		return false;
    	}
    }
    
    /*solve the quadratic formula to determine time in frames needed to travel a distance d with a unit
    	given its acceleration a and top speed v. All distances are in pixels. So far assumes you start from no movement
    	
    	Not used yet because probes accelerate too fast
    */
    public static double timeToMove(UnitType u, float d) {
    	double v = u.topSpeed();
    	
    	//halt distance has some really weird units
    	double distToTopSpeed = u.haltDistance()*0.004;
    	double a = 1/((2.0*distToTopSpeed)/(v*v));
    	double timeToTopSpeed = v/a;
    	if(d <= distToTopSpeed) {
    		return (int) Math.sqrt(2*d/a);
    	} else {
    		return (int) (timeToTopSpeed + (d-distToTopSpeed)/v);
    	}	    	
    }
    
    //tells you if you can afford to build something without cutting higher priority units
    public boolean canAfford(UnitType u) {
    	return (self.minerals() >= u.mineralPrice() + reservedMinerals
    			&& (u.gasPrice() == 0 || self.gas() >= u.gasPrice() + reservedGas));
    }    
    //tells you if you can afford to build something without cutting higher priority units
    public boolean canAfford(UnitType u, int mineralsWhileMoving, int gasWhileMoving) {
    	int requiredMinerals = u.mineralPrice() - mineralsWhileMoving;
    	int sufficientVespeneGas = u.gasPrice() - gasWhileMoving;
    	
    	if(!(saveFor != null && saveFor == u || u == UnitType.Protoss_Pylon)) {
    		requiredMinerals += reservedMinerals;
    		sufficientVespeneGas += reservedGas;
    	}
    	return (self.minerals() >= requiredMinerals
    			&& (u.gasPrice() == 0 || self.gas() >= sufficientVespeneGas));
    }    
    //tells you if you can afford to build something without cutting higher priority units
    public boolean canAfford(UpgradeType u) {
    	return (self.minerals() >= u.mineralPrice() + reservedMinerals
    			&& (u.gasPrice() == 0 || self.gas() >= u.gasPrice() + reservedGas));
    }
    
    //tells you if you can afford to build something without cutting higher priority units
    public boolean canAfford(TechType u) {
    	return (self.minerals() >= u.mineralPrice() + reservedMinerals
    			&& (u.gasPrice() == 0 || self.gas() >= u.gasPrice() + reservedGas));
    }
    
    public static void main(String[] args) {
        new GuiBot().run();
    }
}

