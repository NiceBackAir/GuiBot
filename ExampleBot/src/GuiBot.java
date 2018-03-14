import java.util.HashMap;
import java.util.Map;

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

public class GuiBot extends DefaultBWListener {

    private Mirror mirror = new Mirror();
    
    private Game game;

    private Player self;
    
    private Chokepoint naturalChoke;
    private BaseLocation natural;

    private UnitType nextItem;
    private int nextItemX;
    private int nextItemY;
    
    public static final int PIXELS_PER_TILE = 32;
    public static final float SECONDS_PER_FRAME = 0.042f;
    
    private float mineralsPerFrame;
    private float gasPerFrame;
    private float supplyPerFrame;
    
    private int reservedMinerals;
    private int reservedGas;
    
    //21 Nexus build
    private UnitType[] selectedBO; 
    private boolean[] BOChecklist;
    private UnitType saveFor;
    private int saveForIndex;
    private int BOProgress;
    private int pullGas;
    private int resumeGas;
    private int gasCap;    
    
    private Unit scoutingProbe;
    private TilePosition enemyMain;
    private Position attackPosition;
    
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
    	try {
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
	    	}
	        if(unit.getType() == selectedBO[selectedBO.length-1] && BOProgress >= selectedBO.length-1) {
	        	game.sendText("Build completed in: " + game.elapsedTime() + " seconds.");
	        }
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}
    }
    
    @Override
    public void onUnitDestroy(Unit unit) { 
    	//scouting probes keep suiciding for now
//    	try {
//	    	if(unit.getID() == scoutingProbe.getID()) {
//	    		Unit p = null;
//	    		for(Unit u: self.getUnits()) {
//	    			if(u.getType() == UnitType.Protoss_Probe && (p == null 
//	    				|| u.getPosition().getApproxDistance(unit.getPosition()) < p.getPosition().getApproxDistance(unit.getPosition()))) {
//	    				
//	    				p = u;
//	    			}
//	    		}
//	    		scoutingProbe = p;
//	    	}
//    	} catch (Exception e) {
//    		e.printStackTrace(System.out);
//    	}
    }
    
    @Override
    public void onUnitComplete(Unit unit) {        
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
        
        //initialize everything
        nextItem = null;
        nextItemX = 0;
        nextItemY = 0;
        
        saveFor = null;
        saveForIndex = 0;
        BOProgress = 0;
        pullGas = 0;
        resumeGas = 0;
        gasCap = 3;
        
        
        scoutingProbe = null;
        enemyMain = null;
        
        naturalChoke = null;
        natural = null;
        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
        	for(Position position : baseLocation.getRegion().getPolygon().getPoints()){
        		System.out.print(position + ", ");
        	}
        	System.out.println();
        	
        	if(!baseLocation.getPosition().equals(BWTA.getStartLocation(self).getPosition()) && !baseLocation.isMineralOnly() 
        		&& !baseLocation.isIsland() && (natural == null
        		|| baseLocation.getGroundDistance(BWTA.getStartLocation(self)) < natural.getGroundDistance(BWTA.getStartLocation(self)))) {
        		natural = baseLocation;
        	}
        }

        for(Chokepoint choke: natural.getRegion().getChokepoints()) {
        	if(naturalChoke == null || BWTA.getStartLocation(self).getDistance(choke.getCenter()) 
        	> BWTA.getStartLocation(self).getDistance(naturalChoke.getCenter())) {
        		naturalChoke = choke;
        	}
        }
        
        //player control enable
        game.enableFlag(1);
        game.setLocalSpeed(1);

        selectedBO = pickBuild(game.enemy().getRace());
        
        BOChecklist = new boolean[selectedBO.length];
        
        //nextItemX = self.getStartLocation().getX();
        //nextItemY = self.getStartLocation().getY() + 4;
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
    				UnitType.Protoss_Cybernetics_Core,    				
    				UnitType.Protoss_Zealot,
    				UnitType.Protoss_Pylon, 
    				UnitType.Protoss_Zealot,
    				UnitType.Protoss_Stargate,
    				UnitType.Protoss_Zealot, 
    				UnitType.Protoss_Robotics_Facility};
            
            saveForIndex = 0;
            pullGas = 0;
            resumeGas = 0;
    	} else {
    		chosenBuild = new UnitType[]{UnitType.Protoss_Nexus,
    				UnitType.Protoss_Pylon,     
    				UnitType.Protoss_Gateway,
    				UnitType.Protoss_Assimilator, 
    				UnitType.Protoss_Cybernetics_Core,
    				UnitType.Protoss_Zealot,
    				UnitType.Protoss_Pylon,
    				UnitType.Protoss_Dragoon,
    				UnitType.Protoss_Pylon,
    				UnitType.Protoss_Dragoon,
    				UnitType.Protoss_Robotics_Facility,
    				UnitType.Protoss_Gateway, 
    				UnitType.Protoss_Gateway, 
    				UnitType.Protoss_Dragoon,
    				UnitType.Protoss_Pylon,
    				UnitType.Protoss_Observatory};
            
            saveForIndex = 10;
            pullGas = 0;
            resumeGas = 0;
    	}
    	return chosenBuild;
    }

    @Override
    public void onFrame() {
    	try {
    		game.drawCircleMap(natural.getPoint(), 3, Color.Red);
    		if(naturalChoke != null)
    			game.drawLineMap(naturalChoke.getSides().first, naturalChoke.getSides().second, Color.Red);
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
	        	nextItem = transition();
	        }
	        
	        tic = System.currentTimeMillis();
	        gather();  	        
	        game.drawTextScreen(500, 90, (System.currentTimeMillis()-tic) + " ms gathering");
	        
	        if(scoutingProbe != null) {
	        	scout(scoutingProbe);
	        	game.drawTextMap(scoutingProbe.getPosition(), "Bisu Probe");
	        }
	        tic = System.currentTimeMillis();
	        controlArmy();
	        game.drawTextScreen(500, 105, (System.currentTimeMillis()-tic) + " ms controlling army");
	        
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
		        		
	        BOProgress = Math.max(0, i-1);
	        if(nextItem != null)
	        	game.drawTextScreen(250, 30, "Next Item: " + BOProgress + " " + nextItem.toString());
	        else
	        	game.drawTextScreen(250, 30, "Next Item: -----");
    	} catch (Exception e) {
    		e.printStackTrace(System.out);
    	}
    }    
    /** Keeps track of your own units, income, and BO completion progress
     * 
     */
    public void countUnits() {
        StringBuilder units = new StringBuilder("My units:\n");
        //reset counters
        mineralsPerFrame = 0;
        gasPerFrame = 0;
        //iterate through my units
        for (Unit myUnit : self.getUnits()) {        	
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
//            	if(myUnit.getTarget() ==null) {
//            		game.drawTextMap(myUnit.getPosition(), ""+Math.sqrt(Math.pow(myUnit.getVelocityX(),2) + Math.pow(myUnit.getVelocityY(),2)));
//            	}
            }
        }
        for (Unit neutralUnit : game.neutral().getUnits()) {
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
        }
        //reduce income level to account for saturation
        mineralsPerFrame = Math.min(mineralsPerFrame, self.completedUnitCount(UnitType.Protoss_Nexus)*24*0.0474f);
        
        String BODisplay = "";
        for(int i=0; i<selectedBO.length; i++) {
        	BODisplay += selectedBO[i].toString() + ": " + BOChecklist[i] + "\n";
        }
        game.drawTextScreen(10, 30, BODisplay);
        //game.drawTextScreen(250, 15, "Minerals Per Frame: " + mineralsPerFrame);
        
        //draw my units on screen
        attackPosition = null;
        //now for enemy units
        for (Unit hisUnit: game.enemy().getUnits()) {
        	if(hisUnit.isVisible()) {
	        	//for some reason, the game remembers enemy positions from past games, so gotta check if explored
	        	if (game.isExplored(hisUnit.getTilePosition()) && hisUnit.getType().isResourceDepot()) {
	        		for(TilePosition t: game.getStartLocations()) {
	        			if(t.equals(hisUnit.getTilePosition())) {
	        				enemyMain = hisUnit.getTilePosition();        				
	        			}
	        		}
	        	}
	        	if (enemyMain == null && hisUnit.getType().isBuilding()) {
	        		enemyMain = hisUnit.getTilePosition();
	        	}
	        	if(attackPosition == null || hisUnit.getPosition().getApproxDistance(self.getStartLocation().toPosition()) 
	        		< attackPosition.getApproxDistance(self.getStartLocation().toPosition()) && hisUnit.getType() != UnitType.Zerg_Overlord
	        		&& hisUnit.getType() != UnitType.Zerg_Larva && hisUnit.getType() != UnitType.Zerg_Egg
	        		&& (!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)) {
	        		
	        		attackPosition = hisUnit.getPosition();
	        	}
        	}
        }
        if(attackPosition == null) {
        	//if no units to attack, go for buildings
	        for(Unit hisUnit: game.enemy().getUnits()) {
	        	if(hisUnit.isVisible() && (attackPosition == null || hisUnit.getPosition().getApproxDistance(self.getStartLocation().toPosition()) 
	        		< attackPosition.getApproxDistance(self.getStartLocation().toPosition()))) {
	        		
	        		attackPosition = hisUnit.getPosition();
	        	}
	        }   
        }
    	if(attackPosition == null) {
    		//if no buildings to attack, go to default main
    		if(enemyMain != null) {
	    		attackPosition = enemyMain.toPosition();
    		} else {
    	    	attackPosition = BWTA.getNearestChokepoint(self.getStartLocation().toPosition()).getCenter();
    		}
    	}
    	
    	if(enemyMain != null)
    		game.drawTextMap(enemyMain.toPosition(), "I see you!");
    }
    
    /** Train units and research tech and upgrades. The buildings are sorted by priority order.
     * 
     */
    public void trainAndResearch () {
        for (Unit myUnit : self.getUnits()) {
            //if there's enough minerals, train a probe
            if (myUnit.getType() == UnitType.Protoss_Nexus && myUnit.isCompleted()
            	&& self.completedUnitCount(UnitType.Protoss_Probe) <= self.allUnitCount(UnitType.Protoss_Nexus)*30
            	&& self.completedUnitCount(UnitType.Protoss_Probe) <= 90) {          	
     
            	if(self.completedUnitCount(UnitType.Protoss_Probe) < 4 && self.minerals() > 50 && !myUnit.isTraining()) {
            		//short circuit for when all probes are killed
            		myUnit.build(UnitType.Protoss_Probe);
            	} else {
            		prepareUnit(myUnit, UnitType.Protoss_Probe);
            	}
            }
        }        
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Cybernetics_Core
            	&& (game.enemy().getRace() != Race.Zerg || BOProgress >= selectedBO.length)) {
            	prepareUpgrade(myUnit, UpgradeType.Singularity_Charge);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Robotics_Support_Bay) {
            	prepareUpgrade(myUnit, UpgradeType.Gravitic_Drive);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Citadel_of_Adun) {
            	prepareUpgrade(myUnit, UpgradeType.Leg_Enhancements);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Templar_Archives) {
            	prepareUpgrade(myUnit, TechType.Psionic_Storm);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Forge && BOProgress >= selectedBO.length) {
            	prepareUpgrade(myUnit, UpgradeType.Protoss_Ground_Weapons);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Arbiter_Tribunal) {
            	prepareUpgrade(myUnit, TechType.Stasis_Field);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Robotics_Facility && myUnit.isCompleted()) {
            	if(myUnit.isTraining()) {
            	} else if(self.allUnitCount(UnitType.Protoss_Shuttle) == 0) {
            		prepareUnit(myUnit, UnitType.Protoss_Shuttle);
            	} else if(self.allUnitCount(UnitType.Protoss_Observer) == 0) {
            		prepareUnit(myUnit, UnitType.Protoss_Observer);
            	} else if(self.allUnitCount(UnitType.Protoss_Reaver) == 0) {
                	prepareUnit(myUnit, UnitType.Protoss_Reaver);
            	} else if(self.allUnitCount(UnitType.Protoss_Observer) < 2) {
            		prepareUnit(myUnit, UnitType.Protoss_Observer);
            	} else if(self.allUnitCount(UnitType.Protoss_Reaver) < 2) {
                	prepareUnit(myUnit, UnitType.Protoss_Reaver);
            	} else if(self.allUnitCount(UnitType.Protoss_Shuttle) < 2) {
                	prepareUnit(myUnit, UnitType.Protoss_Shuttle);
            	} else if(self.allUnitCount(UnitType.Protoss_Observer) < 5) {
            		prepareUnit(myUnit, UnitType.Protoss_Observer);
            	}
            }     
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Gateway) {
	            if(myUnit.isCompleted()) {
	            	if(BOProgress < selectedBO.length) {
	            		if(selectedBO[BOProgress] == UnitType.Protoss_Dragoon) {
	            			prepareUnit(myUnit, UnitType.Protoss_Dragoon);
	            		} else if(selectedBO[BOProgress] == UnitType.Protoss_Zealot) {
	            			prepareUnit(myUnit, UnitType.Protoss_Zealot);
	            		}
	            	} else if(self.completedUnitCount(UnitType.Protoss_Templar_Archives) > 0 
	            		&& self.allUnitCount(UnitType.Protoss_High_Templar) < 6) {
	            		
	            		prepareUnit(myUnit, UnitType.Protoss_High_Templar);
	            	} else if(game.enemy().getRace() == Race.Terran || game.enemy().getRace() == Race.Protoss){
	            		prepareUnit(myUnit, UnitType.Protoss_Dragoon);
	            	} else {
	            		prepareUnit(myUnit, UnitType.Protoss_Zealot);
	            	}
            	}
            }     
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Stargate) {
            	if(!myUnit.isTraining()) {
            		if (BOProgress < selectedBO.length) {
            			if(selectedBO[BOProgress] == UnitType.Protoss_Corsair) {
            				prepareUnit(myUnit, UnitType.Protoss_Corsair);
            			}
            		} else if(game.enemy().getRace() == Race.Zerg && self.allUnitCount(UnitType.Protoss_Corsair) < 6) {
            			prepareUnit(myUnit, UnitType.Protoss_Corsair);
            		} else if(game.enemy().getRace() == Race.Terran) {
            			prepareUnit(myUnit, UnitType.Protoss_Arbiter);
            		}
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
    	if (building.isCompleted()) {
        	if (building.canUpgrade(u) && canAfford(u)) {
            	building.upgrade(u);
        	} else if(self.getUpgradeLevel(u) == 0 && !building.isUpgrading()) {	         	
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
    
    /** Makes sure you always save enough money for key upgrades and upgrade if you can
     * 
     * @param Upgrader building
     * @param The upgrade or research you want
     */
    public void prepareUpgrade(Unit building, TechType u) {
    	if (building.isCompleted()) {
        	if (building.canResearch(u) && canAfford(u)) {
            	building.research(u);
        	} else if(!self.hasResearched(u) && !building.isUpgrading()) {	         	
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
    
    /**Makes sure you always save enough money to continue production and produce if you can.
     * Also, update supply requirements.
     * @param building
     * @param myUnit
     */
    public void prepareUnit(Unit building, UnitType unit) {
    	if(self.supplyUsed() + unit.supplyRequired() < self.supplyTotal()) {
	    	if(building.isTraining()) {
	    		if(BOProgress >= selectedBO.length) {
		    		//make sure there will be enough money to build the next unit 		
		    		reservedMinerals += (int) Math.max(0, unit.mineralPrice() 
		    				- building.getRemainingTrainTime() * mineralsPerFrame);
		    		reservedGas += (int) Math.max(0, unit.gasPrice() 
		    				- building.getRemainingTrainTime() * gasPerFrame);
	    		}
	    	} else if(canAfford(unit) && building.canTrain(unit)) {
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
    public UnitType transition() {
    	UnitType nextBuilding = null;
    	
    	double pylonSupplyPerFrame = 0;
    	pylonSupplyPerFrame = self.incompleteUnitCount(UnitType.Protoss_Pylon)*UnitType.Protoss_Pylon.supplyProvided()/(1.0*UnitType.Protoss_Pylon.buildTime())
    			+ self.incompleteUnitCount(UnitType.Protoss_Nexus)*UnitType.Protoss_Nexus.supplyProvided()/(1.0*UnitType.Protoss_Nexus.buildTime());
    	
    	if(pylonSupplyPerFrame <= supplyPerFrame && self.supplyUsed() >= self.supplyTotal() - 20 && self.supplyTotal() < 400) {
    		//don't get supply blocked
    		nextBuilding = UnitType.Protoss_Pylon;
    	} else if(self.allUnitCount(UnitType.Protoss_Assimilator) < self.completedUnitCount(UnitType.Protoss_Nexus)) {
    		//TODO: deal with gasless bases
    		nextBuilding = UnitType.Protoss_Assimilator;
    	} else if(self.allUnitCount(UnitType.Protoss_Gateway) <3) {
    		nextBuilding = UnitType.Protoss_Gateway;   
    	} else if(self.allUnitCount(UnitType.Protoss_Observatory) == 0 
    		&& self.completedUnitCount(UnitType.Protoss_Robotics_Facility) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Observatory;
    	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Support_Bay) == 0 
    		&& self.completedUnitCount(UnitType.Protoss_Robotics_Facility) > 0) {
    			
    		nextBuilding = UnitType.Protoss_Robotics_Support_Bay; 		
    	} else if(self.allUnitCount(UnitType.Protoss_Forge) == 0) {
    		nextBuilding = UnitType.Protoss_Forge;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Nexus) <3) {
    		nextBuilding = UnitType.Protoss_Nexus;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Citadel_of_Adun) == 0 
    		&& self.completedUnitCount(UnitType.Protoss_Cybernetics_Core) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Citadel_of_Adun;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Templar_Archives) == 0
    		&& self.completedUnitCount(UnitType.Protoss_Citadel_of_Adun) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Templar_Archives;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Stargate) == 0
    		&& self.completedUnitCount(UnitType.Protoss_Cybernetics_Core) > 0
    		&& self.completedUnitCount(UnitType.Protoss_Templar_Archives) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Stargate;    		
    	} else if(game.enemy().getRace() == Race.Terran && self.allUnitCount(UnitType.Protoss_Arbiter_Tribunal) == 0
    		&& self.completedUnitCount(UnitType.Protoss_Stargate) > 0
    		&& self.completedUnitCount(UnitType.Protoss_Templar_Archives) > 0) {
    		
    		nextBuilding = UnitType.Protoss_Arbiter_Tribunal;    		
    	} else if(self.allUnitCount(UnitType.Protoss_Templar_Archives) > 0 
    		&& self.allUnitCount(UnitType.Protoss_Gateway) < self.allUnitCount(UnitType.Protoss_Nexus)*3 ) {
    		nextBuilding = UnitType.Protoss_Gateway;        		
    	} else if(self.allUnitCount(UnitType.Protoss_Templar_Archives) > 0)  {
    		nextBuilding = UnitType.Protoss_Nexus;    	
    	}
    	return nextBuilding;
    }
    
    /** Tell workers when/where to gather
     * 
     */
    public void gather() {
    	for (Unit myUnit : self.getUnits()) {
            //if it's a worker and it's idle or just returned minerals, send it to the best mineral patch
            if (myUnit.isCompleted() && myUnit.getType() == UnitType.Protoss_Probe) {
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
            	if(myUnit.isIdle()) {
            		gatherMinerals(myUnit, false);
            	} else if (myUnit.isGatheringMinerals() && myUnit.isCarryingMinerals() && !myUnit.getLastCommand().isQueued()) {
            		myUnit.returnCargo(false);
            		gatherMinerals(myUnit, true);
            	} else if (myUnit.isGatheringMinerals() && !myUnit.isCarryingMinerals() && myUnit.getLastCommand().isQueued()) {
            		gatherMinerals(myUnit, false);
            	}   
            }
        }    	
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
    public void gatherMinerals(Unit myUnit, boolean shiftQ) {
    	Unit closestMineral = null;
    	
    	Unit closestBase = null;
    	for (Unit u: self.getUnits()) {
    		if(u.getType() == UnitType.Protoss_Nexus && u.isCompleted()) {
    			if(closestBase == null || myUnit.getPosition().getApproxDistance(u.getPosition()) 
    				< myUnit.getPosition().getApproxDistance(closestBase.getPosition())) {
    				
    				closestBase = u;
    			}
    		}
    	}    	
    	
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
        }
    }
    
    /** Find the enemy main and scout with probe
     * 
     */
    public void scout(Unit myUnit) {
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
            	for(BaseLocation b: BWTA.getBaseLocations()) {
    	    		if(!(b.isIsland() && !myUnit.isFlying()) && !game.isVisible(b.getTilePosition()) && 
    	    			(likelyExpo == null || b.getDistance(enemyMain.toPosition()) 
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
        	myUnit.move(likelyExpo.getPosition());
    		for(Unit hisUnit: myUnit.getUnitsInRadius(myUnit.getType().sightRange()+32)) {
				if(hisUnit.getPlayer().equals(game.enemy())				
					&& (!hisUnit.getType().isBuilding() || hisUnit.getType() == UnitType.Terran_Bunker)) {
					
	    			if(myUnit.isFlying() && myUnit.isDetected() && !hisUnit.getType().airWeapon().equals(WeaponType.None)) {	
	    				myUnit.move(self.getStartLocation().toPosition());
	    			} else if (!myUnit.isFlying() && !hisUnit.getType().groundWeapon().equals(WeaponType.None)) {
	    				gatherMinerals(myUnit, false);
	    			}
				}
    		} 		
    	}
    }
    
    /** Micro army to optimize army value and gather at certain locations
     * 
     */
    public void controlArmy() {
    	//default attack position if you can't see anything    	
	    
    	game.drawLineMap(new Position(attackPosition.getX() - 5,  attackPosition.getY() - 5), 
    		new Position(attackPosition.getX() + 5,  attackPosition.getY() + 5), Color.Red);
    	game.drawLineMap(new Position(attackPosition.getX() + 5,  attackPosition.getY() - 5), 
        	new Position(attackPosition.getX() - 5,  attackPosition.getY() + 5), Color.Red);
    	
    	Unit closestEnemy;
    	Unit closestSquishy;
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
    		if(myUnit.isCompleted() && !myUnit.getType().isBuilding()) {// && !myUnit.getType().isWorker()) {
				for(Unit hisUnit: myUnit.getUnitsInRadius(myUnit.getType().sightRange())) { 				
					if(hisUnit.getPlayer().equals(game.enemy()) && hisUnit.isVisible(self) && !hisUnit.isInvincible() 					
						&& (!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)
						&& hisUnit.getType() != UnitType.Zerg_Larva && hisUnit.getType() != UnitType.Zerg_Egg) {
						
						if(closestEnemy == null ||	myUnit.getPosition().getApproxDistance(hisUnit.getPosition()) < myUnit.getPosition().getApproxDistance(closestEnemy.getPosition())) {  						
							closestEnemy = hisUnit;
						}
						
						if(!hisUnit.getType().isBuilding())
							canStorm = true;
						
						if(!hisUnit.isFlying() && (closestGroundSquishy == null
							||	myUnit.getPosition().getApproxDistance(hisUnit.getPosition()) < myUnit.getPosition().getApproxDistance(closestGroundSquishy.getPosition()))) {
							
							closestGroundSquishy = hisUnit;
						}				
					}    				
				}	
				closestSquishy = closestEnemy;
				for(Unit hisUnit: myUnit.getUnitsInRadius(myUnit.getType().sightRange()+32)) {    				
					if(hisUnit.getPlayer().equals(game.enemy()) && hisUnit.isVisible(self) && hisUnit.isDetected()) {
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
				
	    		if(myUnit.getType() == UnitType.Protoss_Dragoon) {		 
	    			if(closestEnemy != null && myUnit.getDistance(closestEnemy) - 16*hisSpeedTowardsMe <= myRange) {    	
	    				//frame perfect kiting
	    				//known issue: isUnderAttack is when the unit is flashing on the minimap, continues for a few seconds even after being atked
	    				if(myUnit.getGroundWeaponCooldown()>0 && myUnit.getGroundWeaponCooldown()<25 && myUnit.isUnderAttack()) {
	    					myUnit.move(self.getStartLocation().toPosition());
	    				} else if(!myUnit.isHoldingPosition() && myUnit.getGroundWeaponCooldown()==0) {
	    					//&& myUnit.getDistance(closestEnemy) <= self.weaponMaxRange(WeaponType.Phase_Disruptor)) {
	    					//if(myRange > hisRange + 16*closestEnemy.getType().topSpeed()
	    					//	&& !closestEnemy.getType().groundWeapon().equals(WeaponType.None)
		    				//	&& myUnit.getDistance(closestEnemy) <= hisRange + 16*closestEnemy.getType().topSpeed()) {
	    					//if(myRange - 6*(myUnit.getType().topSpeed() - hisSpeedTowardsMe) > hisRange	&& closestEnemy.canAttackUnit(myUnit)
	    					//	&& myUnit.getDistance(closestEnemy) <= hisRange + 7*(myUnit.getType().topSpeed() - hisSpeedTowardsMe)) {
	    						//kite more before attacking
	    						//myUnit.move(self.getStartLocation().toPosition());	    						
	    					//} else {
	    						myUnit.holdPosition();  								   
	    					//}
	    				} else {
	    					//issue no commands in order to go through with attack
	    					game.drawLineMap(myUnit.getPosition(), closestEnemy.getPosition(), Color.Yellow);
	    				}
	    				game.drawTextMap(myUnit.getPosition(), myUnit.getGroundWeaponCooldown()+"");
	    			} else if(myUnit.isUnderAttack() || myUnit.isUnderDisruptionWeb() || myUnit.isUnderStorm()) {
	    				myUnit.move(self.getStartLocation().toPosition());
	    			} else {
	    				myUnit.attack(attackPosition);
	    			}
	    		} else if(myUnit.getType() == UnitType.Protoss_Zealot) {  	    				 
    				if (myUnit.getGroundWeaponCooldown() > 0 
    					|| closestGroundSquishy != null && myUnit.getGroundWeaponCooldown() 
    					> myUnit.getPosition().getApproxDistance(closestGroundSquishy.getPosition())/myUnit.getType().topSpeed()
    					|| closestGroundEnemy != null && myUnit.getGroundWeaponCooldown() 
    					> myUnit.getPosition().getApproxDistance(closestGroundEnemy.getPosition())/myUnit.getType().topSpeed()) {
    					if(enemyMain != null)
    						myUnit.move(enemyMain.toPosition());
    					else
    						myUnit.move(attackPosition);
    				} else if(myUnit.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Unit
    					&& myUnit.getLastCommand().getTarget().exists()) {
    					//let attack finish
    					game.drawTextMap(myUnit.getPosition(), myUnit.getLastCommand().getTarget() +"");
    				} else if(closestGroundSquishy != null) {
    					if(myUnit.isInWeaponRange(closestGroundSquishy)) {
    						myUnit.attack(closestGroundSquishy);
    					} else if(myUnit.getPosition().getApproxDistance(closestGroundSquishy.getPosition()) > myUnit.getType().seekRange()) {
    						myUnit.move(closestGroundSquishy.getPosition());
    					}
    				}  else if(closestGroundEnemy != null) {
    					if(myUnit.isInWeaponRange(closestGroundEnemy)) {
    						myUnit.attack(closestGroundEnemy);
    					} else if(myUnit.getPosition().getApproxDistance(closestGroundEnemy.getPosition()) > myUnit.getType().seekRange()) {
    						myUnit.move(closestGroundEnemy.getPosition());
    					}    					
    				} else if(self.getUpgradeLevel(UpgradeType.Leg_Enhancements) > 0 || self.completedUnitCount(UnitType.Protoss_Zealot) >= 12) {
    					myUnit.move(attackPosition);
    				} else if(intersects(myUnit.getType(), myUnit.getTilePosition(), naturalChoke.getSides().first, naturalChoke.getSides().second)) {
    					myUnit.holdPosition();
    				} else if(naturalChoke.getCenter().getApproxDistance(myUnit.getPosition()) < naturalChoke.getWidth()/2.0) {
    					if(natural.getPosition().getApproxDistance(myUnit.getPosition()) 
    					< natural.getPosition().getApproxDistance(naturalChoke.getCenter())) {
    					
    						myUnit.move(attackPosition);
    					} else {
    						myUnit.move(self.getStartLocation().toPosition());
    					}
    				} else
    					myUnit.move(naturalChoke.getCenter());
	    		} else if(myUnit.getType() == UnitType.Protoss_Shuttle) {  
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
		    			    		&& BWTA.getRegion(myUnit.getPosition()).equals(BWTA.getRegion(closestGroundSquishy.getPosition()))) { 
		    						
		    						if(myUnit.getDistance(closestGroundSquishy) 
		    							> self.weaponMaxRange(UnitType.Protoss_Scarab.groundWeapon()) - 32)
		    							
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
		    							|| myUnit.getDistance(closestSquishy) > self.weaponMaxRange(u.getType().groundWeapon()) - 32)
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
    					for(Unit u: self.getUnits()) {
    						if(u.getType() == UnitType.Protoss_Reaver && !u.isLoaded()) {
    							if (closestGroundSquishy == null && u.getTrainingQueue().size() + u.getScarabCount() == 5
    								|| closestGroundSquishy != null && (u.getTrainingQueue().size() == 1 && u.getRemainingTrainTime() > 90 
    								|| u.getTrainingQueue().size() > 1)) {		
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
					//sending obs to cloaked things has priority over the whole map
					for(Unit hisUnit: game.enemy().getUnits()) {    				
						if(hisUnit.isVisible(self) && (hisUnit.isCloaked() || hisUnit.isBurrowed())
		    				&& (closestCloaked == null || hisUnit.getDistance(attackPosition) < closestCloaked.getDistance(attackPosition))) {
							
							closestCloaked = hisUnit;
						}    				
					}
					
	    			if(myUnit.isUnderAttack() || (closestEnemy != null && !closestEnemy.getType().airWeapon().equals(WeaponType.None)
	    				&& closestEnemy.getDistance(myUnit) < game.enemy().weaponMaxRange(closestEnemy.getType().airWeapon()) + 32)) {
	    				
	    				myUnit.move(self.getStartLocation().toPosition());
	    			} else {
	    				if(closestCloaked != null) {
	    					myUnit.move(closestCloaked.getPosition());
	    				} else {
	    					scout(myUnit);
	    				}		    				
	    			}
    				//abm
    				if(myUnit.getVelocityX() == 0 && myUnit.getVelocityY() == 0) {
    					myUnit.move(new Position(myUnit.getX() + (int)(12*(Math.random()- 0.5)),myUnit.getY()));
    				}
	    		} else if(myUnit.getType() == UnitType.Protoss_Reaver) {  
	    			if(self.minerals() >=25) {//canAfford(UnitType.Protoss_Scarab)) {
	    				myUnit.train(UnitType.Protoss_Scarab);
	    			}
	    			game.drawTextMap(myUnit.getPosition(), myUnit.getGroundWeaponCooldown()+"");
	    			
	    		} else if(myUnit.getType() == UnitType.Protoss_Corsair) {  	    			
	    			if(myUnit.isUnderAttack() || (closestEnemy != null && !closestEnemy.getType().airWeapon().equals(WeaponType.None)
		    			&& closestEnemy.getDistance(myUnit) < game.enemy().weaponMaxRange(closestEnemy.getType().airWeapon()) + 32)) {
		    				
	    				myUnit.move(self.getStartLocation().toPosition());
	    			} else if(weakestAirEnemy != null && weakestAirEnemy.isDetected()) {
	    				if(myUnit.canAttack(weakestAirEnemy) && myUnit.getAirWeaponCooldown() == 0) {
	    					myUnit.attack(weakestAirEnemy);
	    				} else {
	    					myUnit.move(weakestAirEnemy.getPosition());
	    				}
    					
    				} else {
    					scout(myUnit);
    				}		    				

    				//abm
    				if(myUnit.getVelocityX() == 0 && myUnit.getVelocityY() == 0) {
    					myUnit.move(new Position(myUnit.getX() + (int)(12*(Math.random()- 0.5)),myUnit.getY()));
    				}		    		
	    		} else if(myUnit.getType() == UnitType.Protoss_High_Templar) {  
	    			boolean gotCommand = false;
	    			if(myUnit.getEnergy() < 75 || !self.hasResearched(TechType.Psionic_Storm)) {
	    				myUnit.move(self.getStartLocation().toPosition());
	    				gotCommand = true;
	    			} else if (canStorm) {
	    				HashMap<TilePosition, Double> stormMap = new HashMap<TilePosition, Double>();
	    				TilePosition tempTile = null;
	    				int playerFactor;
	    				double tileFactor;
	    				double cloakFactor;
	    				for (Unit u: myUnit.getUnitsInRadius(9*PIXELS_PER_TILE)) {
	    					if(!u.getType().isBuilding() && !u.isUnderStorm() && u.getType() != UnitType.Zerg_Larva) {
	    						playerFactor = 0;
	    						tileFactor = 1;
	    						cloakFactor = 1;
	    						
	    						if(u.getPlayer() == game.enemy()) {
	    							playerFactor = 1;
	    							if(!u.isDetected() && u.isVisible()) {
	    								cloakFactor = 2;
	    							}
	    						} else if(u.getPlayer() == self) {
	    							playerFactor = -1;
	    						}
	    						
	    						for(int x = -1; x<=1; x++) {
	    							for(int y = -1; y<=1; y++) {
	    								if(x==0 && y== 0) {
	    									tileFactor = 1;
	    								} else {
	    									tileFactor = 0.8;
	    								}
	    								tempTile = new TilePosition(u.getTilePosition().getX() +x, u.getTilePosition().getY() + y);
	        							if(stormMap.get(tempTile) == null) {
	        								stormMap.put(tempTile, playerFactor*tileFactor*cloakFactor);
	        							} else {
	        								stormMap.replace(tempTile, stormMap.get(tempTile) + playerFactor*tileFactor*cloakFactor);
	        							}
	    							}
	    						}

	    					}
	    				}
	    				
	    				//find the best storm spot
	    				TilePosition stormLocation = null;
	    				double mostCasualties = 0;
	    				for(Map.Entry<TilePosition, Double> entry: stormMap.entrySet()) {
	    					//set storm minimum here
	    					if(entry.getValue() >= 3.5 && entry.getValue() > mostCasualties) {
	    						mostCasualties = entry.getValue();
	    						stormLocation = entry.getKey();
	    					}
	    				}
	    				if(stormLocation != null) {
	    					myUnit.useTech(TechType.Psionic_Storm, new Position(stormLocation.getX()*32 + 16, stormLocation.getY()*32 + 16));
	    					gotCommand = true;
	    					game.sendText(mostCasualties+"");
	    				}
	    			}
	    			if(!gotCommand) {
		    			if (closestEnemy != null && !closestEnemy.getType().groundWeapon().equals(WeaponType.None)
		    		    	&& closestEnemy.getDistance(myUnit) < game.enemy().weaponMaxRange(closestEnemy.getType().groundWeapon()) + 32) {
		    				myUnit.move(self.getStartLocation().toPosition());
		    			} else {
		    				myUnit.move(attackPosition);
		    			}
	    			}
	    			game.drawTextMap(myUnit.getPosition(), myUnit.getSpellCooldown()+"");
	    		} else if(myUnit.getType() == UnitType.Protoss_Probe) {
	    			if(closestEnemy != null && (!closestEnemy.getType().groundWeapon().equals(WeaponType.None) || closestEnemy.getType().isSpellcaster()
	    				|| closestEnemy.getType() == UnitType.Terran_Dropship || closestEnemy.getType() == UnitType.Protoss_Shuttle)) {	    				
	    				Unit drillPatch = null;
    					for(Unit n: game.getNeutralUnits()) {
    						if(n.getType().isResourceContainer()
    							&& BWTA.getRegion(n.getPosition()).equals(BWTA.getRegion(self.getStartLocation()))) {
    						
    							if(drillPatch == null || n.getDistance(closestEnemy.getPosition()) < drillPatch.getDistance(closestEnemy.getPosition())) {
    								drillPatch = n;
    							}
    						}
    					}
    					if(myUnit.getLastCommand().getUnitCommandType() != UnitCommandType.Attack_Unit
    						|| myUnit.isUnderAttack()) {
	    					if(drillPatch != null) {
	    						myUnit.rightClick(drillPatch);
	    					} else {
	    						myUnit.move(self.getStartLocation().toPosition());
	    					}
		    				if(game.getUnitsOnTile(myUnit.getTilePosition()).size() > 3
		    					&& BWTA.getRegion(myUnit.getPosition()).equals(BWTA.getRegion(self.getStartLocation()))) {
		    					
		    					for(Unit n: game.getNeutralUnits()) {
		    						if(n.getType().isResourceContainer()
		    							&& !BWTA.getRegion(n.getPosition()).equals(BWTA.getRegion(self.getStartLocation()))) {
		    						
		    							if(drillPatch == null || n.getDistance(closestEnemy.getPosition()) < drillPatch.getDistance(closestEnemy.getPosition())) {
		    								drillPatch = n;
		    							}
		    						}
		    					}
		    					game.sendText(""+game.getUnitsOnTile(myUnit.getTilePosition()).size());
		    					if(mySpeedTowardsHim > 0)
		    						myUnit.rightClick(drillPatch);
		    					else
		    						myUnit.attack(closestEnemy);
		    				}
    					}
	    			}
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
    public void updateBuildingMap(UnitType nextItem) {
    	Position myBase = new Position(self.getStartLocation().toPosition().getX() + 2*PIXELS_PER_TILE,
    								self.getStartLocation().toPosition().getY() + (int) 1.5*PIXELS_PER_TILE);
    	TilePosition baseTile = self.getStartLocation();
    	Position closestChoke = BWTA.getNearestChokepoint(myBase).getCenter();
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
		if(game.enemy().getRace() == Race.Zerg && BOProgress < 8 && (nextItem != UnitType.Protoss_Pylon || BOProgress ==1)
			&& nextItem != UnitType.Protoss_Nexus && naturalChoke != null) {
			
			TilePosition p1 = naturalChoke.getSides().first.toTilePosition();
			TilePosition p2 = naturalChoke.getSides().second.toTilePosition();
			if(nextItem == UnitType.Protoss_Photon_Cannon) {
		    	for(int x = Math.min(p1.getX(), p2.getX()) - nextItem.tileWidth() -3; x <= Math.max(p1.getX(), p2.getX()) +3; x += 1) {
		        	for(int y = Math.min(p1.getY(), p2.getY()) -nextItem.tileHeight() -3; y <= Math.max(p1.getY(), p2.getY()) +3; y += 1) {
		        		nextTile = new TilePosition(x,y);		        		
		        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
		        									, nextTile.toPosition().getY() + nextItem.tileHeight()*16);		        		
		        		
	            		if(shouldBuildHere(nextItem, nextTile, closestChoke, bestPosition)) {
	            			game.drawCircleMap(nextTile.toPosition(), 3, Color.Green);
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
		            		if(shouldBuildHere(nextItem, nextTile, closestChoke, bestPosition)) {
		            			game.drawCircleMap(nextTile.toPosition(), 3, Color.Green);
			            		bestTile = nextTile;
			        			bestPosition = buildingCenter;
		            		}
		            	} else {
		            		// build pylon farther back if choke is short
		            		if(shouldBuildHere(nextItem, nextTile, natural.getPosition(), bestPosition)) {
		            			game.drawCircleMap(nextTile.toPosition(), 3, Color.Green);
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
		        		
		        		if(intersects(nextItem, nextTile, naturalChoke.getSides().first, naturalChoke.getSides().second)) {				        			
		            		if(shouldBuildHere(nextItem, nextTile, closestChoke, bestPosition)) {
		            			game.drawCircleMap(nextTile.toPosition(), 3, Color.Green);
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
		    	for(int x = baseTile.getX() - 2; x <= baseTile.getX() + 4; x +=2) {
		        	for(int y = baseTile.getY() - 2; y <= baseTile.getY() + 4; y +=2) {	  
		        		nextTile = new TilePosition(x,y);
		        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
								, nextTile.toPosition().getY() + nextItem.tileHeight()*16);	
		        		
	            		if(shouldBuildHere(nextItem, nextTile, closestChoke, bestPosition)) {
		            		bestTile = nextTile;
		        			bestPosition = buildingCenter;
	            		}
		        	}
		    	}	    	
		    	if(bestTile == null) {	    		
		    		//build a spotter pylon
			    	for(int x = baseTile.getX() - 25; x <= baseTile.getX() + 27; x += 7) {
			        	for(int y = baseTile.getY() - 30; y <= baseTile.getY() + 32; y += 3) {	 
			        		nextTile = new TilePosition(x,y);
			        		nextPosition = nextTile.toPosition();
				    		if(BWTA.getRegion(baseTile).getPolygon().isInside(nextPosition) && game.canBuildHere(nextTile, nextItem) &&
				    			(bestTile == null || baseTile.getDistance(nextTile) > baseTile.getDistance(bestTile))) {
				    			
				    			bestTile = nextTile;
				    			bestPosition = buildingCenter;
				    		}
			        	}
		    		}
		    	}
	    	} else if (nextItem == UnitType.Protoss_Assimilator) {
	            for (Unit neutralUnit : game.neutral().getUnits()) {            	
	                if (neutralUnit.getType() == UnitType.Resource_Vespene_Geyser) {
	                	boolean needsGas = false;
	                	for(Unit u: game.getUnitsOnTile(BWTA.getNearestBaseLocation(neutralUnit.getTilePosition()).getTilePosition())) {
	                		if(u.isCompleted() && u.getType().isResourceDepot() && u.getPlayer().equals(self)) {
	                			needsGas = true;
	                		}
	                	}
	                	if (game.canBuildHere(neutralUnit.getTilePosition(), nextItem) && needsGas
	                		&& (bestTile == null || baseLocation.getDistance(neutralUnit.getTilePosition()) < baseLocation.getDistance(bestTile))) {
	                		bestTile = neutralUnit.getTilePosition();
	                	}
	                }
	            } 
	    	} else if (nextItem == UnitType.Protoss_Nexus) {  		
	            for (BaseLocation b : BWTA.getBaseLocations()) {  
	            	boolean baseTaken = false;
	            	TilePosition t = b.getTilePosition();
	            	for(Unit u: game.getUnitsOnTile(t)) {
	            		if(u.getType().isResourceDepot()) {
	            			baseTaken = true;
	            		}
	            	}
	        		if (!b.isIsland() && !b.isMineralOnly() && !baseTaken 
	        			&& (bestTile == null || closestChoke.getApproxDistance(t.toPosition()) < closestChoke.getApproxDistance(bestTile.toPosition()))) {
	            		bestTile = t;
	            	}
	            }   
	    	} else {
		    	for(int x = baseTile.getX() - 6; x <= baseTile.getX() + 8; x +=4) {
		        	for(int y = baseTile.getY() - 6; y <= baseTile.getY() + 8; y +=3) {	        	
		        		//don't place big buildings right next to nexus
		        		if(!(Math.abs(x-baseTile.getX()) <= 4 && Math.abs(y-baseTile.getY()) <= 3)) {
			        		nextTile = new TilePosition(x,y);
			        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
									, nextTile.toPosition().getY() + nextItem.tileHeight()*16);	
			        		
		            		if(shouldBuildHere(nextItem, nextTile, closestChoke, bestPosition)) {
			            		bestTile = nextTile;
			        			bestPosition = buildingCenter;
		            		}
		        		}
		        	}
		    	}
		    	if(bestTile == null) {
		    		//build a building close to nexus
			    	for(int x = baseTile.getX() - 29; x <= baseTile.getX() + 27; x += 7) {
			        	for(int y = baseTile.getY() - 34; y <= baseTile.getY() + 32; y += 4) {	 
			        		if(!(Math.abs(x-baseTile.getX()) <= 4 && Math.abs(y-baseTile.getY()) <= 3)) {
				        		nextTile = new TilePosition(x,y);
				        		nextPosition = nextTile.toPosition();
				        		buildingCenter = new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*16
										, nextTile.toPosition().getY() + nextItem.tileHeight()*16);	
					    		if(BWTA.getRegion(baseTile).getPolygon().isInside(nextPosition)) {					        		
				            		if(shouldBuildHere(nextItem, nextTile, baseTile.toPosition(), bestPosition)) {
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
    	} else {
    		backupTile = getBackupTile(baseTile, nextItem);
    		if(backupTile != null) {    			
	    		nextItemX = backupTile.getX();
	    		nextItemY = backupTile.getY();
    		}
    	}
    	game.drawBoxMap(nextItemX*PIXELS_PER_TILE, nextItemY*PIXELS_PER_TILE, (nextItemX+nextItem.tileWidth())*PIXELS_PER_TILE,
    			(nextItemY+nextItem.tileHeight())*PIXELS_PER_TILE, new Color(0,255,255));
    }
    
    /** Enhances the game.canBuildHere method, ignoring the building probe for collision and leaving space for expos
     * 
     * @param myUnit
     * @param buildingLocation
     * @return
     */
    public boolean shouldBuildHere(UnitType myUnit, TilePosition nextTile, Position homingPoint, Position bestPoint) {
		Unit builderProbe = null;
    	Position buildingCenter = new Position(nextTile.toPosition().getX() + myUnit.tileWidth()*16
				, nextTile.toPosition().getY() + myUnit.tileHeight()*16);		  
    	
    	//check for optimal placement
    	if(bestPoint != null && homingPoint.getApproxDistance(buildingCenter) >= homingPoint.getApproxDistance(bestPoint))
    		return false;    	
    	
		int blockingUnits = 0;
    	for(Unit u: game.getUnitsInRectangle(nextTile.toPosition(), 
    		new Position(nextTile.toPosition().getX() + nextItem.tileWidth()*32, nextTile.toPosition().getY() + nextItem.tileHeight()*32))) {
    		
    		if(u.getPlayer() == self && u.getType() == UnitType.Protoss_Probe && u.getLastCommand().getTargetTilePosition().equals(nextTile)) {
    			builderProbe = u;
    		} else {
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
		for(Unit u: game.getUnitsInRectangle(
			nextTile.toPosition().getX()-2*PIXELS_PER_TILE, nextTile.toPosition().getY()-2*PIXELS_PER_TILE,
			nextTile.toPosition().getX()+3*PIXELS_PER_TILE, nextTile.toPosition().getY()+3*PIXELS_PER_TILE)) {
			
			if(u.getType().isResourceContainer() || u.getType().isRefinery()) {
				return false;
			}
		}
		
    	return true;
    }
    
    /** Uses cross products to determine whether a building touches a line on the map
     * 
     */
    public boolean intersects (UnitType building, TilePosition buildingLocation, Position a, Position b) {
    	int bx = buildingLocation.toPosition().getX();
    	int by = buildingLocation.toPosition().getY();
    	int[][] corners = {{bx, by}, {bx + building.tileWidth()*PIXELS_PER_TILE, by}, {bx, by + building.tileHeight()*PIXELS_PER_TILE}, 
    						{bx + building.tileWidth()*PIXELS_PER_TILE, by + building.tileHeight()*PIXELS_PER_TILE}};
    	
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
	        		if(shouldBuildHere(nextItem, nextTile, baseTile.toPosition(), bestPosition)) {
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
		        		if(shouldBuildHere(nextItem, nextTile, baseTile.toPosition(), bestPosition)) {
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
    public boolean buildBuilding(UnitType building, int x, int y) {   
    	//decide where to put the building    	
    	TilePosition buildingLoc = new TilePosition(x,y);
    	
    	//find the worker to build with
    	double shortestDist = 999999;
    	Unit closestWorker = null;
    	UnitCommand command = null;
    	boolean match = false;
    	double dist;
    	for (Unit myUnit : self.getUnits()) {
            //build buildings at the optimal time
            if (myUnit.getType().isWorker() && !myUnit.isGatheringGas() && myUnit.isCompleted() && !match && !myUnit.equals(scoutingProbe) &&
            	(game.canBuildHere(buildingLoc, building, myUnit, true) || !game.isVisible(buildingLoc))) {
            	
            	command = myUnit.getLastCommand();            
            	dist = myUnit.getPosition().getApproxDistance(buildingLoc.toPosition());
            	if(myUnit.isConstructing() || command.getTargetTilePosition().equals(buildingLoc)) {
            		//prevent multiple workers from building the same thing
            		closestWorker = myUnit;
            		shortestDist = dist;
            		match = true;
            	} else if (dist < shortestDist) {
            		closestWorker = myUnit;
            		shortestDist = dist;
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
    		if(game.canBuildHere(buildingLoc, building, closestWorker, true) && fullyExplored
    			&& self.minerals() >= building.mineralPrice() && self.gas() >= building.gasPrice()) {     
    			closestWorker.build(building, buildingLoc);
//    			reservedMinerals += building.mineralPrice();
//    			reservedGas += building.gasPrice();
    		} else {
    			closestWorker.move(buildingLoc.toPosition());    			
    		}
    		game.drawTextMap(closestWorker.getPosition(), "Builder");
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
    	
    	if(saveFor == null || saveFor != u) {
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

