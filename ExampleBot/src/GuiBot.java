import java.util.HashMap;

import bwapi.Color;
import bwapi.DefaultBWListener;
import bwapi.Game;
import bwapi.Mirror;
import bwapi.Player;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitCommand;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwapi.WeaponType;
import bwta.BWTA;
import bwta.*;

public class GuiBot extends DefaultBWListener {

    private Mirror mirror = new Mirror();
    
    private Game game;

    private Player self;

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
    private UnitType[] PvTBO;
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
    
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
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
    		Unit p = null;
    		for(Unit u: self.getUnits()) {
    			if(u.getType() == UnitType.Protoss_Probe && (p == null || u.getDistance(unit.getPosition()) < p.getDistance(unit.getPosition()))) {
    				p = u;
    			}
    		}
    		scoutingProbe = p;
    	}
        if(unit.getType() == UnitType.Protoss_Robotics_Facility) {
        	game.sendText("Build completed in: " + game.elapsedTime() + " seconds.");
        }
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
        
        selectedBO = PvTBO; 
        saveFor = null;
        saveForIndex = 0;
        BOProgress = 0;
        pullGas = 0;
        resumeGas = 0;
        gasCap = 3;
        
        
        scoutingProbe = null;
        enemyMain = null;
        
        
        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
        	for(Position position : baseLocation.getRegion().getPolygon().getPoints()){
        		System.out.print(position + ", ");
        	}
        	System.out.println();
        }
        
        //player control enable
        game.enableFlag(1);
        game.setLocalSpeed(1);
        
        PvTBO = new UnitType[]{UnitType.Protoss_Nexus,
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
        
        selectedBO = PvTBO;
        
        BOChecklist = new boolean[selectedBO.length];
        
        //nextItemX = self.getStartLocation().getX();
        //nextItemY = self.getStartLocation().getY() + 4;
    }

    @Override
    public void onFrame() {
    	try {
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
	        if (BOProgress == saveForIndex && BOProgress > 0) {
	        	saveFor = selectedBO[saveForIndex];
	        	reservedMinerals += saveFor.mineralPrice();
	        	reservedGas += saveFor.gasPrice();        	
	        }
	
	        countUnits();        
	        
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
	        
	        trainAndResearch();     
	        
	        //true if initial BO is complete
	        if(keepChecking) {
	        	nextItem = transition();
	        }
	        
	        gather();        
	        
	        if(scoutingProbe != null) {
	        	probeScout();
	        }
	        
	        controlArmy();
	
	        if (nextItem.isBuilding()) {
	            updateBuildingMap(nextItem);
	        	buildBuilding(nextItem, nextItemX, nextItemY);   
	        }
		        		
	        BOProgress = Math.max(0, i-1);
	        if(nextItem != null)
	        	game.drawTextScreen(250, 30, "Next Item: " + BOProgress + " " + nextItem.toString());
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
            	if(myUnit.getTarget() !=null) {
            		game.drawTextMap(myUnit.getPosition(), myUnit.getTarget().getType().toString());
            	}
            } else if(myUnit.getType().isBuilding()) {
            	boolean match = false;
            	int i = 0;
            	while(!match && i<BOChecklist.length) {
            		if (PvTBO[i] == myUnit.getType() && !BOChecklist[i]) {
            			BOChecklist[i] = true;  
            			match = true;
            		}
            		i++;
            	}
            } else {
            	if(myUnit.getTarget() !=null) {
            		game.drawTextMap(myUnit.getPosition(), myUnit.getTarget().getType().toString());
            	}
            }
        }
        
        //reduce income level to account for saturation
        mineralsPerFrame = Math.max(mineralsPerFrame, self.completedUnitCount(UnitType.Protoss_Nexus)*24);
        
        game.drawTextScreen(10, 30, PvTBO[0].toString() + ": " + BOChecklist[0] + "\n"
        		+ PvTBO[1].toString() + ": " + BOChecklist[1] + "\n"
        		+ PvTBO[2].toString() + ": " + BOChecklist[2] + "\n"
        		+ PvTBO[3].toString() + ": " + BOChecklist[3] + "\n"
        		+ PvTBO[4].toString() + ": " + BOChecklist[4] + "\n"
        		+ PvTBO[5].toString() + ": " + BOChecklist[5] + "\n"
        		+ PvTBO[6].toString() + ": " + BOChecklist[6] + "\n"
        		+ PvTBO[7].toString() + ": " + BOChecklist[7] + "\n"
        		+ PvTBO[8].toString() + ": " + BOChecklist[8] + "\n"
        		+ PvTBO[9].toString() + ": " + BOChecklist[9] + "\n"
        		+ PvTBO[10].toString() + ": " + BOChecklist[10] + "\n"
        		+ PvTBO[11].toString() + ": " + BOChecklist[11] + "\n"
        		+ PvTBO[12].toString() + ": " + BOChecklist[12] + "\n"
        		+ PvTBO[13].toString() + ": " + BOChecklist[13] + "\n"
        		);
        //game.drawTextScreen(250, 15, "Minerals Per Frame: " + mineralsPerFrame);
        
        //draw my units on screen
        
        //now for enemy units
        for (Unit hisUnit: game.enemy().getUnits()) {
        	//for some reason, the game remembers enemy positions from past games, so gotta check if explored
        	if (game.isExplored(hisUnit.getTilePosition()) && hisUnit.getType() == UnitType.Terran_Command_Center 
        		|| hisUnit.getType() == UnitType.Zerg_Hatchery || hisUnit.getType() == UnitType.Protoss_Nexus) {
        		for(TilePosition t: game.getStartLocations()) {
        			if(t.equals(hisUnit.getTilePosition())) {
        				enemyMain = hisUnit.getTilePosition();        				
        			}
        		}
        	}
        }
    }
    
    /** Train units and research tech and upgrades. The buildings are sorted by priority order.
     * 
     */
    public void trainAndResearch () {
        for (Unit myUnit : self.getUnits()) {
            //if there's enough minerals, train a probe
            if (myUnit.getType() == UnitType.Protoss_Nexus && myUnit.isCompleted()
            	&& self.completedUnitCount(UnitType.Protoss_Probe) <= self.completedUnitCount(UnitType.Protoss_Probe)*30) {
            	prepareUnit(myUnit, UnitType.Protoss_Probe);
            }
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Cybernetics_Core) {
            	prepareUpgrade(myUnit, UpgradeType.Singularity_Charge);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Robotics_Support_Bay) {
            	prepareUpgrade(myUnit, UpgradeType.Gravitic_Drive);
        	}
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Robotics_Facility && myUnit.isCompleted()) {
            	if(myUnit.isTraining()) {
            	} else if(self.completedUnitCount(UnitType.Protoss_Shuttle) == 0) {
            		prepareUnit(myUnit, UnitType.Protoss_Shuttle);
            	} else if(self.completedUnitCount(UnitType.Protoss_Observer) == 0) {
            		prepareUnit(myUnit, UnitType.Protoss_Observer);
            	} else if(self.completedUnitCount(UnitType.Protoss_Reaver) == 0) {
                	prepareUnit(myUnit, UnitType.Protoss_Reaver);
            	}
            }     
        }
        for (Unit myUnit : self.getUnits()) {
            if (myUnit.getType() == UnitType.Protoss_Gateway) {
	            if(myUnit.isCompleted() 
	            	&& (BOProgress < selectedBO.length && selectedBO[BOProgress] == UnitType.Protoss_Dragoon
	            	|| BOProgress >= selectedBO.length)) {
	            	if(BOProgress < selectedBO.length) {
	            		if(!myUnit.isTraining() && canAfford(UnitType.Protoss_Dragoon)) {
	            			myUnit.train(UnitType.Protoss_Dragoon);
	            		}
	            	} else {
	            		prepareUnit(myUnit, UnitType.Protoss_Dragoon);
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
    public void prepareUpgrade(Unit upgradeBuilding, UpgradeType upgrade) {
    	if (upgradeBuilding.isCompleted()) {
        	if (upgradeBuilding.canUpgrade(upgrade) && canAfford(upgrade)) {
            	upgradeBuilding.upgrade(upgrade);
        	} else if(self.getUpgradeLevel(upgrade) == 0 && !upgradeBuilding.isUpgrading()) {	         	
        		reservedMinerals += upgrade.mineralPrice();
        		reservedGas += upgrade.gasPrice();		            	
        	}        	
        } else {
        	reservedMinerals += Math.max(0, upgrade.mineralPrice()
        		- upgradeBuilding.getRemainingBuildTime() * mineralsPerFrame);
    		reservedGas += Math.max(0, upgrade.gasPrice()
    			- upgradeBuilding.getRemainingBuildTime() * gasPerFrame);
        }
    }
    
    /**Makes sure you always save enough money to continue production and produce if you can.
     * Also, update supply requirements.
     * @param productionBuilding
     * @param myUnit
     */
    public void prepareUnit(Unit productionBuilding, UnitType unit) {
    	if(productionBuilding.isTraining()) {
    		//make sure there will be enough money to build the next unit 		
    		reservedMinerals += (int) Math.max(0, unit.mineralPrice() 
    				- productionBuilding.getRemainingTrainTime() * mineralsPerFrame);
    		reservedGas += (int) Math.max(0, unit.gasPrice() 
    				- productionBuilding.getRemainingTrainTime() * gasPerFrame);
    	} else if(canAfford(unit) && productionBuilding.canTrain(unit)) {
    		productionBuilding.train(unit);
    	} else {
    		reservedMinerals += unit.mineralPrice();
    		reservedGas += unit.gasPrice();
    	}
    	supplyPerFrame += unit.supplyRequired()*1f/unit.buildTime();
    }
    
    /** Decide how to spend resources after finishing initial BO. 
     * It doesn't matter whether you can afford the next building, as it will just maintain priority until you can.
     */
    public UnitType transition() {
    	UnitType nextBuilding = null;
    	
    	float pylonSupplyPerFrame = 0;
    	pylonSupplyPerFrame =self.incompleteUnitCount(UnitType.Protoss_Pylon)*8f/UnitType.Protoss_Pylon.buildTime();
    	
    	if(pylonSupplyPerFrame <= supplyPerFrame && self.supplyUsed() >= self.supplyTotal() - 20) {
    		//don't get supply blocked
    		nextBuilding = UnitType.Protoss_Pylon;
    	} /*else if(myUnitTab.get(UnitType.Protoss_Nexus) != null && myUnitTab.get(UnitType.Protoss_Assimilator) != null && myUnitTab.get(UnitType.Protoss_Nexus) > myUnitTab.get(UnitType.Protoss_Assimilator)) {
    		nextBuilding = UnitType.Protoss_Assimilator;
    	}*/ else if(self.allUnitCount(UnitType.Protoss_Gateway) <3) {
    		nextBuilding = UnitType.Protoss_Gateway;   
    	} else if(self.allUnitCount(UnitType.Protoss_Observatory) == 0) {
    		nextBuilding = UnitType.Protoss_Observatory;
    	} else if(self.allUnitCount(UnitType.Protoss_Robotics_Support_Bay) == 0) {
    		nextBuilding = UnitType.Protoss_Robotics_Support_Bay;
    	} else {
    		nextBuilding = UnitType.Protoss_Gateway;    		
    	}
    	return nextBuilding;
    }
    
    /** Tell workers when/where to gather
     * 
     */
    public void gather() {
    	for (Unit myUnit : self.getUnits()) {
            //if it's a worker and it's idle or just returned minerals, send it to the best mineral patch
            if (myUnit.isCompleted() && myUnit.getType() == UnitType.Protoss_Probe && myUnit.isIdle()) {            	
                gatherMinerals(myUnit);
            }
        }    	
    	for (Unit myUnit : self.getUnits()) {
    		int gasCount = 0;
    		if(myUnit.getType() == UnitType.Protoss_Assimilator && myUnit.isCompleted()) {
    			//needs some work for expos wis partial gas saturation
    			for (Unit u: self.getUnits()) {
    				if(u.isGatheringGas()) {
    					gasCount++;
    				}
    			}     
	        	for (Unit u: self.getUnits()) {
	        		if (gasCount > gasCap) {
	        			if(u.isGatheringGas() && !u.isCarryingGas()) {
	        				gatherMinerals(u);
	        				gasCount--;
	        			}
	        		} else if (gasCount < gasCap && (u.isGatheringMinerals() && !u.isCarryingMinerals() || u.isIdle())) {	        			
	        			u.gather(myUnit, false);
	        			gasCount++;
	        		}
	        	}
    		}
    	}
    }
    public void gatherMinerals(Unit myUnit) {
    	Unit closestMineral = null;
    	
    	Unit closestBase = null;
    	for (Unit u: self.getUnits()) {
    		if(u.getType() == UnitType.Protoss_Nexus && u.isCompleted()) {
    			if(closestBase == null || myUnit.getDistance(u) < myUnit.getDistance(closestBase)) {
    				closestBase = u;
    			}
    		}
    	}
    	
    	Position closestBaseCenter = new Position(closestBase.getX() + 2*PIXELS_PER_TILE, closestBase.getY() + (int) 1.5*PIXELS_PER_TILE);
    	
        //find the closest mineral
        for (Unit neutralUnit : game.neutral().getUnits()) {
            if (neutralUnit.getType().isMineralField()) {
                if (closestMineral == null || (closestBaseCenter.getDistance(neutralUnit) < closestBaseCenter.getDistance(closestMineral)
                	&& !neutralUnit.isBeingGathered())) {
                    closestMineral = neutralUnit;
                }
            }
        }

        //if a mineral patch was found, send the worker to gather it
        if (closestMineral != null) {
            myUnit.gather(closestMineral, false);
        }
    }
    
    /** Find the enemy main and scout with probe
     * 
     */
    public void probeScout() {
    	game.drawTextMap(scoutingProbe.getPosition(), "Bisu Probe");
    	if(enemyMain == null) {
	    	TilePosition farthestMain = null;
	    	for(TilePosition t: game.getStartLocations()) {
	    		if(!game.isExplored(t) && (farthestMain == null || t.getDistance(self.getStartLocation()) < farthestMain.getDistance(self.getStartLocation()))) {
	    			farthestMain = t;
	    		}
	    	}
	    	scoutingProbe.move(farthestMain.toPosition());
    	} else {
    		//if there's nothing else to do, go intangible
    		gatherMinerals(scoutingProbe);
    		scoutingProbe = null;
    	}
    }
    
    /** Micro army to optimize army value and gather at certain locations
     * 
     */
    public void controlArmy() {
    	//default attack position if you can't see anything
    	Position attackPosition = new Position(0,0);
    	if(enemyMain != null)
    		attackPosition = enemyMain.toPosition();
    	Position tempPosition = null;
    	
    	//If you can see the target location, pick targets more intelligently
    	if(game.isVisible(attackPosition.toTilePosition())) {
    		for(Unit hisUnit: game.enemy().getUnits()) {
    			if(tempPosition == null || hisUnit.getPosition().getDistance(attackPosition) < tempPosition.getDistance(attackPosition)) {
    				tempPosition = hisUnit.getPosition();
    			}
    		}
    		attackPosition = tempPosition;
    	}
    	//attackPosition goes to null when the game ends, causing errors
    	/*
    	if(attackPosition != null) {
	    	game.drawLineMap(new Position(attackPosition.getX() - 5,  attackPosition.getY() - 5), 
	    		new Position(attackPosition.getX() + 5,  attackPosition.getY() + 5), Color.Red);
	    	game.drawLineMap(new Position(attackPosition.getX() + 5,  attackPosition.getY() - 5), 
	        		new Position(attackPosition.getX() - 5,  attackPosition.getY() + 5), Color.Red);
    	}*/
    	
    	Unit closestEnemy;
    	for(Unit myUnit:self.getUnits()) {
    		closestEnemy = null;
    		if(myUnit.isCompleted() && !myUnit.getType().isWorker()) {
				for(Unit hisUnit: myUnit.getUnitsInRadius(myUnit.getType().sightRange())) {    				
					if(hisUnit.getPlayer().equals(game.enemy())) {
						if((closestEnemy == null ||	myUnit.getDistance(hisUnit.getPosition()) < myUnit.getDistance(closestEnemy.getPosition()))) {    					
							closestEnemy = hisUnit;
						}
					}    				
				}	
	    		if(myUnit.getType() == UnitType.Protoss_Dragoon) {			
	    			if(closestEnemy != null && myUnit.isInWeaponRange(closestEnemy)) {    				
	    				//frame perfect kiting
	    				//known issue: isUnderAttack is when the unit is flashing on the minimap, continues for a few seconds even after being atked
	    				if(myUnit.getGroundWeaponCooldown()>0 && myUnit.getGroundWeaponCooldown()<25 && myUnit.isUnderAttack()) {

	    					myUnit.move(self.getStartLocation().toPosition());
	    				} else if(!myUnit.isHoldingPosition() && myUnit.getGroundWeaponCooldown()==0 ) {
	    		//			&& myUnit.getDistance(closestEnemy) > self.weaponMaxRange(WeaponType.Phase_Disruptor) - PIXELS_PER_TILE) {   		
	    								    				
			    			myUnit.holdPosition();  								    			
	    				} else {
	    					game.drawLineMap(myUnit.getPosition(), closestEnemy.getPosition(), Color.Yellow);
	    				}
	    				game.drawTextMap(myUnit.getPosition(), myUnit.getGroundWeaponCooldown()+"");
	    			} else if(myUnit.isUnderAttack()) {
	    				myUnit.move(self.getStartLocation().toPosition());
	    			} else {
	    				myUnit.attack(attackPosition);
	    			}
	    		} else if(myUnit.getType() == UnitType.Protoss_Shuttle) {  
	    			if(myUnit.getLoadedUnits() != null) {
	    				for(Unit u: myUnit.getLoadedUnits()) {
			    			if(myUnit.isUnderAttack() || (closestEnemy != null && closestEnemy.canAttack(myUnit) && closestEnemy.isInWeaponRange(myUnit))) {
			    				myUnit.move(self.getStartLocation().toPosition());
			    			} else if(closestEnemy != null && myUnit.canUnload(u)) {
			    				myUnit.unload(u);
			    			} else {		    			
			    				myUnit.move(attackPosition);
			    			}
	    				}
	    			} else {
	    				//abm
	    				if(!myUnit.isMoving()) {
	    					myUnit.move(new Position(myUnit.getX() + (int)(2*(Math.random()- 0.5)),myUnit.getY()));
	    				}
	    				if(self.completedUnitCount(UnitType.Protoss_Reaver) != 0) {
	    					for(Unit u: self.getUnits()) {
	    						if(u.getType() == UnitType.Protoss_Reaver && (closestEnemy == null
	    							|| (u.getGroundWeaponCooldown()>0 && u.getGroundWeaponCooldown()<25))) {
	    							
	    							myUnit.load(u);
	    						}
	    					}
	    				}
	    			}
	    		} else if(myUnit.getType() == UnitType.Protoss_Observer) {  
	    			if(myUnit.isUnderAttack() || (closestEnemy != null && closestEnemy.canAttack(myUnit) && closestEnemy.isInWeaponRange(myUnit))) {
	    				myUnit.move(self.getStartLocation().toPosition());
	    			} else {
	    				myUnit.move(attackPosition);
	    			}
	    		} else if(myUnit.getType() == UnitType.Protoss_Reaver) {  
	    			if(myUnit.getScarabCount() == 0 && canAfford(UnitType.Protoss_Scarab)) {
	    				myUnit.train(UnitType.Protoss_Scarab);
	    			}
	    		}	    		
    		}
    	}
    }
    
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
    	double closestDistance = 99999;
    	double d;
    	if(nextItem == UnitType.Protoss_Pylon) {
	    	for(int x = baseTile.getX() - 2; x <= baseTile.getX() + 4; x +=2) {
	        	for(int y = baseTile.getY() - 2; y <= baseTile.getY() + 4; y +=2) {	  
	        		nextTile = new TilePosition(x,y);
	        		d = BWTA.getGroundDistance(nextTile, closestChoke.toTilePosition());
	        		if(game.canBuildHere(nextTile, nextItem) && d < closestDistance) {
	        			closestDistance = d;	
	        			bestTile = nextTile;
	        		}
	        	}
	    	}	    	
	    	if(bestTile != null) {
		    	nextItemX = bestTile.getX();
		    	nextItemY = bestTile.getY();	    	
	    	} else {
	    		Position nextPosition = null;
	    		nextTile = null;
	    		bestTile = null;
	    		//build a spotter pylon
		    	for(int x = baseTile.getX() - 20; x <= baseTile.getX() + 22; x += 7) {
		        	for(int y = baseTile.getY() - 20; y <= baseTile.getY() + 22; y += 3) {	 
		        		nextTile = new TilePosition(x,y);
		        		nextPosition = nextTile.toPosition();
			    		if(BWTA.getRegion(baseTile).getPolygon().isInside(nextPosition) && game.canBuildHere(nextTile, nextItem) &&
			    			(bestTile == null || baseTile.getDistance(nextTile) > baseTile.getDistance(bestTile))) {
			    			
			    			bestTile = nextTile;
			    		}
		        	}
	    		}
		    	if(bestTile != null) {
			    	nextItemX = bestTile.getX();
			    	nextItemY = bestTile.getY();
		    	} else {
		    		//try a different base
		    	}
	    	}
    	} else if (nextItem == UnitType.Protoss_Assimilator) {
            for (Unit neutralUnit : game.neutral().getUnits()) {            	
                if (neutralUnit.getType() == UnitType.Resource_Vespene_Geyser) {
                	if (game.canBuildHere(neutralUnit.getTilePosition(), nextItem) && (bestTile == null || baseLocation.getDistance(neutralUnit.getTilePosition())
                			< baseLocation.getDistance(bestTile))) {
                		bestTile = neutralUnit.getTilePosition();
                	}
                }
            }    
	    	if(bestTile != null) {
		    	nextItemX = bestTile.getX();
		    	nextItemY = bestTile.getY();
	    	} else {
	    		//try a different base
	    	}
    	} else if (nextItem == UnitType.Protoss_Nexus) {    		
            for (BaseLocation b : BWTA.getBaseLocations()) {  
            	TilePosition t = b.getTilePosition();
        		if (!t.equals(baseLocation) && (bestTile == null || closestChoke.getDistance(t.toPosition())
            			< closestChoke.getDistance(bestTile.toPosition()))) {
            		bestTile = t;
            	}
            }    
	    	if(bestTile != null) {
		    	nextItemX = bestTile.getX();
		    	nextItemY = bestTile.getY();
	    	} else {
	    		//try a different base
	    	}
    	} else {
	    	for(int x = baseTile.getX() - 6; x <= baseTile.getX() + 8; x +=4) {
	        	for(int y = baseTile.getY() - 6; y <= baseTile.getY() + 8; y +=3) {	        	
	        		//don't place big buildings right next to nexus
	        		if(!(Math.abs(x-baseTile.getX()) <= 4 && Math.abs(y-baseTile.getY()) <= 3)) {
		        		nextTile = new TilePosition(x,y);
		        		d = BWTA.getGroundDistance(nextTile, closestChoke.toTilePosition());
		        		if(game.canBuildHere(nextTile, nextItem) && d < closestDistance) {
		        			closestDistance = d;	
		        			bestTile = nextTile;
		        		}
	        		}
	        	}
	    	}
	    	if(bestTile != null) {
		    	nextItemX = bestTile.getX();
		    	nextItemY = bestTile.getY();	    	
	    	} else {
	    		Position nextPosition = null;
	    		nextTile = null;
	    		bestTile = null;
	    		//build a building close to nexus
		    	for(int x = baseTile.getX() - 24; x <= baseTile.getX() + 22; x += 7) {
		        	for(int y = baseTile.getY() - 24; y <= baseTile.getY() + 22; y += 4) {	 
		        		if(!(Math.abs(x-baseTile.getX()) <= 4 && Math.abs(y-baseTile.getY()) <= 3)) {
			        		nextTile = new TilePosition(x,y);
			        		nextPosition = nextTile.toPosition();
				    		if(BWTA.getRegion(baseTile).getPolygon().isInside(nextPosition) && game.canBuildHere(nextTile, nextItem) &&
				    			(bestTile == null || baseTile.getDistance(nextTile) < baseTile.getDistance(bestTile))) {
				    			
				    			bestTile = nextTile;
				    		}
		        		}
		        	}
	    		}
		    	if(bestTile != null) {
			    	nextItemX = bestTile.getX();
			    	nextItemY = bestTile.getY();
		    	} else {
		    		//try a different base
		    	}
	    	}
    	}
    	game.drawBoxMap(nextItemX*PIXELS_PER_TILE, nextItemY*PIXELS_PER_TILE, (nextItemX+nextItem.tileWidth())*PIXELS_PER_TILE, (nextItemY+nextItem.tileHeight())*PIXELS_PER_TILE, new Color(0,255,255));
    }
    
    /**build a building at the optimal time
     * returns true if build command was issued, else returns false 
    */
    public boolean buildBuilding(UnitType building, int x, int y) {   
    	//decide where to put the building    	
    	TilePosition buildingLoc = new TilePosition(x,y);
//    	TilePosition baseLocation = self.getStartLocation();
//    	if (building == UnitType.Protoss_Assimilator) {
//    		TilePosition closestGas = null;
//            for (Unit neutralUnit : game.neutral().getUnits()) {            	
//                if (neutralUnit.getType() == UnitType.Resource_Vespene_Geyser) {
//                	if (game.canBuildHere(neutralUnit.getTilePosition(), building) && (closestGas == null || baseLocation.getDistance(neutralUnit.getTilePosition())
//                			< baseLocation.getDistance(closestGas))) {
//                		closestGas = neutralUnit.getTilePosition();
//                	}
//                }
//            }    
//            buildingLoc = closestGas;
//    	} else if (building == UnitType.Protoss_Nexus) {    		
//    		TilePosition closestBase = null;
//    		Chokepoint closestChoke = BWTA.getNearestChokepoint(baseLocation);
//            for (BaseLocation b : BWTA.getBaseLocations()) {  
//            	TilePosition t = b.getTilePosition();
//        		if (!t.equals(baseLocation) &&(closestBase == null || closestChoke.getDistance(t.toPosition())
//            			< closestChoke.getDistance(closestBase.toPosition()))) {
//            		closestBase = t;
//            	}
//            }    
//            buildingLoc = closestBase;
//    	} else if (building == UnitType.Protoss_Pylon) {
//	    	//int tries = 0;
//	    	//while(!game.canBuildHere(buildingLoc, building) && tries < 10) {
//	    		//x+=6;
//	    		buildingLoc = new TilePosition(x,y);           
//	    	//	tries++;		
//	    	//}
//    	} else {
//	    	//int tries = 0;
//	    	//y+=2;
//	    	buildingLoc = new TilePosition(x,y);
//	    	//while(!game.canBuildHere(buildingLoc, building) && tries < 20) {
//	    		//x++;
//	    	//	buildingLoc = new TilePosition(x,y);           
//	    	//	tries++;		
//	    	//}  
//    	}
    	
    	//find the worker to build with
    	int shortestDist = 999999;
    	Unit closestWorker = null;
    	UnitCommand command = null;
    	boolean match = false;
    	for (Unit myUnit : self.getUnits()) {
            //build buildings at the optimal time
            if (myUnit.getType().isWorker() && !myUnit.isGatheringGas() && myUnit.isCompleted() && !match) {
            	command = myUnit.getLastCommand();
            	int dist = myUnit.getDistance(buildingLoc.toPosition());
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
    	   	
    	int mineralsWhileMoving = (int) (shortestDist/UnitType.Protoss_Probe.topSpeed() * (mineralsPerFrame - 0.0474));
    	int gasWhileMoving = (int) (shortestDist/UnitType.Protoss_Probe.topSpeed() * gasPerFrame);
    	
    	//decide whether to build the building
    	if (canAfford(building, mineralsWhileMoving, gasWhileMoving)) {
    		if(game.isExplored(buildingLoc)) {     
    			closestWorker.build(building, buildingLoc);
    		} else {
    			closestWorker.move(buildingLoc.toPosition());    			
    		}
    		return true;
    	} else {
    		return false;
    	}
    }
    
    /*solve the quadratic formula to determine time in frames needed to travel a distance d with a unit
    	given its acceleration a and top speed v. All distances are in pixels. So far assumes you start from no movement
    	
    	Not used yet because probes accelerate too fast
    */
    public static int timeToMove(UnitType u, float d) {
    	float a = u.acceleration();
    	double v = u.topSpeed();
    	double timeToTopSpeed = v/a;
    	double distToTopSpeed = 0.5*a*Math.pow(timeToTopSpeed,2);
    	if(d <= distToTopSpeed) {
    		return (int) Math.sqrt(2*d/a);
    	} else {
    		return (int) (timeToTopSpeed + (d-distToTopSpeed)/v);
    	}	
    	
    }
    
    //tells you if you can afford to build something without cutting higher priority units
    public boolean canAfford(UnitType u) {
    	return game.canMake(u) && (self.minerals() >= u.mineralPrice() + reservedMinerals
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
    	return game.canMake(u) && (self.minerals() >= requiredMinerals
    			&& (u.gasPrice() == 0 || self.gas() >= sufficientVespeneGas));
    }    
    //tells you if you can afford to build something without cutting higher priority units
    public boolean canAfford(UpgradeType u) {
    	return (self.minerals() >= u.mineralPrice() + reservedMinerals
    			&& (u.gasPrice() == 0 || self.gas() >= u.gasPrice() + reservedGas));
    }
    
    public static void main(String[] args) {
        new GuiBot().run();
    }
}

