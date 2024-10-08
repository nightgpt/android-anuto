package ch.logixisland.anuto.business.tower;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.logixisland.anuto.business.game.GameState;
import ch.logixisland.anuto.business.game.ScoreBoard;
import ch.logixisland.anuto.engine.logic.GameEngine;
import ch.logixisland.anuto.engine.logic.entity.Entity;
import ch.logixisland.anuto.engine.logic.entity.EntityRegistry;
import ch.logixisland.anuto.entity.EntityTypes;
import ch.logixisland.anuto.entity.plateau.Plateau;
import ch.logixisland.anuto.entity.tower.Tower;
import ch.logixisland.anuto.util.math.Vector2;

public class TowerInserter {

    public interface Listener {
        void towerInserted();
    }

    private final GameEngine mGameEngine;
    private final GameState mGameState;
    private final EntityRegistry mEntityRegistry;
    private final TowerSelector mTowerSelector;
    private final TowerAging mTowerAging;
    private final ScoreBoard mScoreBoard;

    private final TowerDefaultValue mTowerDefaultValue;

    private Tower mInsertedTower;
    private Plateau mCurrentPlateau;
    private final Collection<Listener> mListeners = new CopyOnWriteArrayList<>();

    public TowerInserter(GameEngine gameEngine, GameState gameState, EntityRegistry entityRegistry,
                         TowerSelector towerSelector, TowerAging towerAging, ScoreBoard scoreBoard) {
        mGameEngine = gameEngine;
        mGameState = gameState;
        mEntityRegistry = entityRegistry;
        mTowerSelector = towerSelector;
        mTowerAging = towerAging;
        mScoreBoard = scoreBoard;

        mTowerDefaultValue = new TowerDefaultValue(entityRegistry);
    }

    public void insertTower(final String towerName) {
        if (mGameEngine.isThreadChangeNeeded()) {
            mGameEngine.post(() -> insertTower(towerName));
            return;
        }

        if (mInsertedTower == null && !mGameState.isGameOver() &&
                mScoreBoard.getCredits() >= mTowerDefaultValue.getDefaultValue(towerName)) {
            showTowerLevels();
            mInsertedTower = (Tower) mEntityRegistry.createEntity(towerName);
        }
    }

    public void setPosition(final Vector2 position) {
        if (mGameEngine.isThreadChangeNeeded()) {
            mGameEngine.post(() -> setPosition(position));
            return;
        }

        if (mInsertedTower != null) {
            Plateau closestPlateau = mGameEngine.getEntitiesByType(EntityTypes.PLATEAU)
                    .cast(Plateau.class)
                    .filter(Plateau.unoccupied())
                    .min(Entity.distanceTo(position));

            if (closestPlateau != null) {
                if (mCurrentPlateau == null) {
                    mGameEngine.add(mInsertedTower);
                    mTowerSelector.selectTower(mInsertedTower);
                }

                mCurrentPlateau = closestPlateau;
                mInsertedTower.setPosition(mCurrentPlateau.getPosition());
            } else {
                cancel();
            }
        }
    }

    public void buyTower() {
        if (mGameEngine.isThreadChangeNeeded()) {
            mGameEngine.post(this::buyTower);
            return;
        }

        if (mInsertedTower != null && mCurrentPlateau != null) {
            mInsertedTower.setPlateau(mCurrentPlateau);
            mInsertedTower.setBuilt();

            mScoreBoard.takeCredits(mInsertedTower.getValue());
            mTowerAging.ageTower(mInsertedTower);

            mTowerSelector.selectTower(null);
            hideTowerLevels();

            mCurrentPlateau = null;
            mInsertedTower = null;

            for (Listener listener : mListeners) {
                listener.towerInserted();
            }
        }
    }

    public void cancel() {
        if (mGameEngine.isThreadChangeNeeded()) {
            mGameEngine.post(this::cancel);
            return;
        }

        if (mInsertedTower != null) {
            mGameEngine.remove(mInsertedTower);

            hideTowerLevels();
            mCurrentPlateau = null;
            mInsertedTower = null;
        }
    }

    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    private void showTowerLevels() {
        Iterator<Tower> towers = mGameEngine.getEntitiesByType(EntityTypes.TOWER).cast(Tower.class);

        while (towers.hasNext()) {
            Tower tower = towers.next();
            tower.showLevel();
        }
    }

    private void hideTowerLevels() {
        Iterator<Tower> towers = mGameEngine.getEntitiesByType(EntityTypes.TOWER).cast(Tower.class);

        while (towers.hasNext()) {
            Tower tower = towers.next();
            tower.hideLevel();
        }
    }

}
