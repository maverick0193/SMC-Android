package rs.pedjaapps.smc.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.PerformanceCounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import rs.pedjaapps.smc.MaryoGame;
import rs.pedjaapps.smc.audio.MusicManager;
import rs.pedjaapps.smc.audio.SoundManager;
import rs.pedjaapps.smc.ga.GA;
import rs.pedjaapps.smc.object.Box;
import rs.pedjaapps.smc.object.GameObject;
import rs.pedjaapps.smc.object.World;
import rs.pedjaapps.smc.object.maryo.Maryo;
import rs.pedjaapps.smc.utility.Constants;
import rs.pedjaapps.smc.utility.GameSave;
import rs.pedjaapps.smc.utility.LevelGenerator;
import rs.pedjaapps.smc.utility.NAHudText;
import rs.pedjaapps.smc.utility.PrefsManager;
import rs.pedjaapps.smc.utility.Utility;
import rs.pedjaapps.smc.view.ConfirmDialog;
import rs.pedjaapps.smc.view.HUD;

public class GameScreen extends AbstractScreen implements InputProcessor
{
    private World world;
    public OrthographicCamera cam;
    private OrthographicCamera pCamera;
    public OrthographicCamera guiCam;

    ShapeRenderer shapeRenderer = new ShapeRenderer();

    private ParticleEffect leafEffect;

    private SpriteBatch spriteBatch;
    private boolean debug = PrefsManager.isDebug();

    private BitmapFont debugFont, debugObjectFont;
    private GlyphLayout debugGlyph;

    public HUD hud;

    private float width, height;

    public String levelName;

    public void setGameState(GAME_STATE gameState)
    {
        this.gameState = gameState;
        hud.updateTimer = !(gameState == GAME_STATE.PLAYER_DEAD || gameState == GAME_STATE.PLAYER_UPDATING || gameState == GAME_STATE.NO_UPDATE);
    }

    public GAME_STATE getGameState()
    {
        return gameState;
    }

    public enum GAME_STATE
    {
        GAME_READY, GAME_RUNNING, GAME_PAUSED, GAME_OVER, PLAYER_DEAD,
        NO_UPDATE, PLAYER_UPDATING
    }

    private GAME_STATE gameState;

    private HashMap<Integer, TouchInfo> touches = new HashMap<>();
    LevelGenerator generator;

    Sound audioOn;
    Music music;

    float goAlpha = 0.0f;
    boolean goTouched = false;

    public boolean isDebug()
    {
        return debug;
    }

    public void setDebug(boolean debug)
    {
        this.debug = debug;
    }

    ConfirmDialog exitDialog;

    public KillPointsTextHandler killPointsTextHandler;

    public boolean resumed;
    private float stateTime;

    private double accumulator;
    private float step = 1.0f / 30.0f;
    private String objectDebugText;
    PerformanceCounter performanceCounter = new PerformanceCounter("pc");

    public GameScreen(MaryoGame game)
    {
        super(game);
        gameState = GAME_STATE.GAME_READY;
        width = Gdx.graphics.getWidth();
        height = Gdx.graphics.getHeight();
        world = new World(this);
        hud = new HUD(world);
        this.cam = new OrthographicCamera(Constants.CAMERA_WIDTH, Constants.CAMERA_HEIGHT);
        this.cam.setToOrtho(false, Constants.CAMERA_WIDTH, Constants.CAMERA_HEIGHT);
        this.cam.update();

        pCamera = new OrthographicCamera(Constants.CAMERA_WIDTH, Constants.CAMERA_HEIGHT);
        pCamera.setToOrtho(false, Constants.CAMERA_WIDTH, Constants.CAMERA_HEIGHT);
        pCamera.position.set(Constants.CAMERA_WIDTH / 2f, Constants.CAMERA_HEIGHT / 2f, 0);
        pCamera.update();

        guiCam = new OrthographicCamera(width, height);
        guiCam.position.set(width / 2f, height / 2f, 0);
        guiCam.update();

        spriteBatch = new SpriteBatch();

        loadTextures();

        for (int i = 0; i < 5; i++) //handle max 4 touches
        {
            touches.put(i, new TouchInfo());
        }
        generator = new LevelGenerator(world);
        //Gdx.graphics.setContinuousRendering(false);
        GameSave.startLevelFresh();

        exitDialog = new ConfirmDialog(this, guiCam);
    }

    @Override
    public void show()
    {
        music = game.assets.manager.get(world.level.music);
        MusicManager.play(music);
        if (debug) GLProfiler.enable();
        Gdx.input.setCatchBackKey(true);
        Gdx.input.setInputProcessor(this);
        if (!resumed)
        {
            GA.sendLevelStarted(levelName);
        }
    }

    int physicsAccumulatorIterations;

    @Override
    public void render(float delta)
    {
        //debug
        //long now = System.currentTimeMillis();
        //debug

        delta = 1f / 60f;

        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        //physics
        updateObjects(delta);
        //physics end

        if (gameState == GAME_STATE.GAME_RUNNING) generator.update(cam);

        moveCamera(cam, world.maryo.position);
        drawBackground();

        world.level.parallaxGround2.render(cam, spriteBatch);
        world.level.parallaxGround1.render(cam, spriteBatch);
        world.level.parallaxClouds.render(cam, spriteBatch);

        spriteBatch.setProjectionMatrix(cam.combined);
        spriteBatch.begin();
        drawObjects();
        killPointsTextHandler.render(spriteBatch, delta);
        spriteBatch.end();

        spriteBatch.setProjectionMatrix(cam.combined);
        spriteBatch.begin();
        world.maryo._render(spriteBatch);
        spriteBatch.end();

        spriteBatch.setProjectionMatrix(pCamera.combined);
        spriteBatch.begin();
        leafEffect.draw(spriteBatch);

        if (gameState == GAME_STATE.GAME_RUNNING) leafEffect.update(delta);
        spriteBatch.end();

        spriteBatch.setProjectionMatrix(guiCam.combined);
        spriteBatch.begin();
        if (debug) drawDebugText();
        spriteBatch.end();
        if (debug) drawDebug();

        hud.render(gameState, delta);

        if (gameState == GAME_STATE.GAME_OVER)
        {
            handleGameOver(delta);
        }

        exitDialog.render(spriteBatch);

        //cleanup
        for (int i = 0; i < world.level.gameObjects.size; i++)
        {
            GameObject go = world.level.gameObjects.get(i);
            removeObject(world.level.gameObjects, cam, go);
        }
        for (int i = 0; i < world.level.parallaxClouds.objects.size; i++)
        {
            GameObject go = world.level.parallaxClouds.objects.get(i);
            removeObject( world.level.parallaxClouds.objects, world.level.parallaxClouds.cam, go);
        }
        for (int i = 0; i < world.level.parallaxGround2.objects.size; i++)
        {
            GameObject go = world.level.parallaxGround2.objects.get(i);
            removeObject(world.level.parallaxGround2.objects, world.level.parallaxGround2.cam, go);
        }
        for (int i = 0; i < world.level.parallaxGround1.objects.size; i++)
        {
            GameObject go = world.level.parallaxGround1.objects.get(i);
            removeObject(world.level.parallaxGround1.objects, world.level.parallaxGround1.cam, go);
        }

        if (debug) GLProfiler.reset();
        stateTime += delta;

        //debug
        //long end = System.currentTimeMillis() - now;
        //System.ou t.println("render time total: " + end);
        //debug
    }

    private void removeObject(Array<GameObject> objects, OrthographicCamera cam, GameObject go)
    {
        if (go.mDrawRect.x + go.mDrawRect.width < cam.position.x - cam.viewportWidth * .5f)
        {
            if(objects.removeValue(go, true))
            {
                go.dispose();
            }
        }
    }

    public void showBoxText(Box box)
    {
        setGameState(GAME_STATE.NO_UPDATE);
        hud.boxTextPopup.show(box, this);
    }

    private void handleGameOver(float delta)
    {
        if (GameSave.save.lifes < 0)
        {
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glEnable(GL20.GL_BLEND);

            shapeRenderer.setProjectionMatrix(guiCam.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0, 0, 0, 0.5f);
            shapeRenderer.rect(0, 0, width, height);
            shapeRenderer.end();

            spriteBatch.setProjectionMatrix(guiCam.combined);
            spriteBatch.begin();

            Texture go = game.assets.manager.get("data/game/game_over.png");
            go.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            float width = this.width * 0.8f;
            float height = width / 4;

            float x = this.width / 2 - width / 2;
            float y = this.height / 2 - height / 2;
            spriteBatch.draw(go, x, y, width, height);

            spriteBatch.end();
            if (!goTouched) return;
        }

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_BLEND);

        shapeRenderer.setProjectionMatrix(guiCam.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, goAlpha += 0.033f);
        shapeRenderer.rect(0, 0, width, height);
        shapeRenderer.end();

        spriteBatch.setProjectionMatrix(cam.combined);
        spriteBatch.begin();
        spriteBatch.end();
        //background changes to black if i don't add this after blend

        if (goAlpha >= 1)
        {
            if (GameSave.save.lifes < 0)
            {
                game.setScreen(new LoadingScreen(new MainMenuScreen(game), false));
            }
            else
            {
                game.setScreen(new LoadingScreen(new GameScreen(game), false));
            }
        }
    }

    private void drawBackground()
    {
        world.level.background.render(cam, spriteBatch);
    }

    public void moveCamera(OrthographicCamera cam, Vector3 pos)
    {
        if ((gameState == GAME_STATE.PLAYER_UPDATING))
            return;
        pos = World.VECTOR3_POOL.obtain().set(pos);
        pos.x = pos.x + cam.viewportWidth * .25f;
        if (pos.x - cam.viewportWidth / 2 < 0)
        {
            pos.x = cam.viewportWidth / 2;
        }

        if (pos.y - cam.viewportHeight / 2 < 0)
        {
            pos.y = cam.viewportHeight / 2;
        }
        cam.position.set(pos);
        cam.update();
        World.VECTOR3_POOL.free(pos);
    }

    private void updateObjects(float delta)
    {
        //performanceCounter.tick();
        //performanceCounter.start();
        //int count = 0;
        Rectangle maryoBWO = world.createMaryoRectWithOffset(cam, 8);
        for (int i = 0; i < world.level.gameObjects.size; i++)
        //for (GameObject go : world.level.gameObjects)
        {
            GameObject go = world.level.gameObjects.get(i);
            if (maryoBWO.overlaps(go.mColRect))
            {
                if (gameState == GAME_STATE.GAME_RUNNING)
                {
                    go._update(delta);
                    /*if(go instanceof Enemy)*/
                    //count++;
                }
            }
        }
        World.RECT_POOL.free(maryoBWO);
        if (gameState == GAME_STATE.GAME_RUNNING || gameState == GAME_STATE.PLAYER_DEAD || gameState == GAME_STATE.PLAYER_UPDATING)
        {
            world.maryo._update(delta);
        }
        //System.out.println(count);
        //performanceCounter.stop();
        //System.out.println(performanceCounter.time.average);
    }

    private void drawObjects()
    {
        for (int i = 0; i < world.level.gameObjects.size; i++)
        {
            world.level.gameObjects.get(i)._render(spriteBatch);
        }
    }

    private void drawDebug()
    {
        // render blocks
        shapeRenderer.setProjectionMatrix(cam.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        // render maryo
        Maryo maryo = world.maryo;
        Rectangle body = maryo.mColRect;
        Rectangle bounds = maryo.mDrawRect;
        Rectangle maryoBWO = world.createMaryoRectWithOffset(cam, 8);
        shapeRenderer.setColor(0, 1, 0, 1);
        shapeRenderer.rect(body.x, body.y, body.width, body.height);
        shapeRenderer.setColor(1, 0, 0, 1);
        shapeRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height);
        shapeRenderer.setColor(0, 0, 1, 1);
        shapeRenderer.rect(maryo.debugRayRect.x, maryo.debugRayRect.y, maryo.debugRayRect.width, maryo.debugRayRect.height);
        shapeRenderer.setColor(0.3f, 0.9f, 0, 0);
        shapeRenderer.rect(maryoBWO.x, maryoBWO.y, maryoBWO.width, maryoBWO.height);
        shapeRenderer.end();
    }

    private void drawDebugText()
    {
        String debugMessage = generateDebugMessage();
        debugGlyph.setText(debugFont, debugMessage);
        debugFont.draw(spriteBatch, debugMessage, 20, height - 20);
    }

    private String generateDebugMessage()
    {
        return
                "\n" + "Player: x=" + world.maryo.position.x + ", y=" + world.maryo.position.y
                        + "\n" + "Player Vel: x=" + world.maryo.velocity.x + ", y=" + world.maryo.velocity.y
                        + "\n" + "World Camera: x=" + cam.position.x + ", y=" + cam.position.y
                        + "\n" + "BG Camera: x=" + world.level.background.bgCam.position.x + ", y=" + world.level.background.bgCam.position.y
                        + "\n" + "JavaHeap: " + Gdx.app.getJavaHeap() / 1000000 + "MB"
                        + "\n" + "NativeHeap: " + Gdx.app.getNativeHeap() / 1000000 + "MB"
                        + "\n" + "OGL Draw Calls: " + GLProfiler.drawCalls
                        + "\n" + "OGL TextureBindings: " + GLProfiler.textureBindings
                        + "\n" + "Object Count: " + (world.level.gameObjects.size + world.level.parallaxClouds.objects.size + world.level.parallaxGround1.objects.size + world.level.parallaxGround2.objects.size)
                        + "\n" + "Render/Physics: 1/" + physicsAccumulatorIterations
                        + "\n" + "Screen w=" + width + "h=" + height
                        + "\n" + "FPS: " + Gdx.graphics.getFramesPerSecond();
    }

    @Override
    public void resize(int width, int height)
    {
        this.width = width;
        this.height = height;

        Constants.initCamera();

        cam = new OrthographicCamera(Constants.CAMERA_WIDTH, Constants.CAMERA_HEIGHT);
        cam.setToOrtho(false, Constants.CAMERA_WIDTH, Constants.CAMERA_HEIGHT);
        cam.update();

        pCamera = new OrthographicCamera(Constants.CAMERA_WIDTH, Constants.CAMERA_HEIGHT);
        pCamera.setToOrtho(false, Constants.CAMERA_WIDTH, Constants.CAMERA_HEIGHT);
        pCamera.position.set(Constants.CAMERA_WIDTH / 2f, Constants.CAMERA_HEIGHT / 2f, 0);
        pCamera.update();

        guiCam = new OrthographicCamera(width, height);
        guiCam.position.set(width / 2f, height / 2f, 0);
        guiCam.update();

        world.level.background.resize(cam);
        world.level.parallaxClouds.resize(cam);
        world.level.parallaxGround1.resize(cam);
        world.level.parallaxGround2.resize(cam);
        exitDialog.resize();
        hud.resize(width, height);
    }

    @Override
    public void hide()
    {
        gameState = GAME_STATE.GAME_PAUSED;
        //Gdx.input.setInputProcessor(null);
        music.stop();
    }

    @Override
    public void pause()
    {
        gameState = GAME_STATE.GAME_PAUSED;
        music.stop();
    }

    @Override
    public void resume()
    {
        //Gdx.input.setInputProcessor(this);
    }

    @Override
    public void dispose()
    {
        music.stop();
        Gdx.input.setInputProcessor(null);
        game.assets.dispose();
        exitDialog.dispose();
        world.dispose();
        GA.sendLevelEnded(levelName, stateTime);
    }

    @Override
    public void loadAssets()
    {
        for (Maryo.MaryoState ms : Maryo.MaryoState.values())
        {
            game.assets.manager.load("data/maryo/" + ms.toString() + ".pack", TextureAtlas.class);
        }
        game.assets.manager.load("data/animation/fireball.pack", TextureAtlas.class);
        game.assets.manager.load("data/animation/particles/fireball_emitter.p", ParticleEffect.class, game.assets.particleEffectParameter);
        game.assets.manager.load("data/animation/particles/fireball_explosion_emitter.p", ParticleEffect.class, game.assets.particleEffectParameter);
        game.assets.manager.load("data/animation/particles/iceball_emitter.p", ParticleEffect.class, game.assets.particleEffectParameter);
        game.assets.manager.load("data/animation/particles/iceball_explosion_emitter.p", ParticleEffect.class, game.assets.particleEffectParameter);
        game.assets.manager.load("data/animation/particles/star_trail.p", ParticleEffect.class, game.assets.particleEffectParameter);
        game.assets.manager.load("data/animation/particles/maryo_star.p", ParticleEffect.class, game.assets.particleEffectParameter);
        game.assets.manager.load("data/animation/iceball.png", Texture.class, game.assets.textureParameter);
        game.assets.manager.load("data/game/game_over.png", Texture.class);
        game.assets.manager.load("data/environment/clouds/clouds.pack", TextureAtlas.class);
        game.assets.manager.load("data/game/items/goldpiece/red.pack", TextureAtlas.class);
        game.assets.manager.load("data/game/items/goldpiece/yellow.pack", TextureAtlas.class);
        game.assets.manager.load("data/environment/background/green_junglehills.png", Texture.class);

        for (LevelGenerator.SpriteDescriptor sd : LevelGenerator.groundDecorationNoParallax)
        {
            game.assets.manager.load(sd.texture, Texture.class);
        }

        for (LevelGenerator.SpriteDescriptor sd : LevelGenerator.groundDecorationParallaxLevel1)
        {
            game.assets.manager.load(sd.texture, Texture.class);
        }

        for (LevelGenerator.SpriteDescriptor sd : LevelGenerator.groundDecorationParallaxLevel2)
        {
            game.assets.manager.load(sd.texture, Texture.class);
        }

        hud.loadAssets();

        //audio
        game.assets.manager.load("data/sounds/audio_on.mp3", Sound.class);
        game.assets.manager.load("data/sounds/item/goldpiece_1.mp3", Sound.class);
        game.assets.manager.load("data/sounds/item/goldpiece_red.mp3", Sound.class);
        game.assets.manager.load("data/sounds/player/dead.mp3", Sound.class);
        game.assets.manager.load("data/sounds/player/jump_big.mp3", Sound.class);
        game.assets.manager.load("data/sounds/player/jump_big_power.mp3", Sound.class);
        game.assets.manager.load("data/sounds/player/jump_small.mp3", Sound.class);
        game.assets.manager.load("data/sounds/player/jump_small_power.mp3", Sound.class);
        game.assets.manager.load("data/sounds/player/jump_ghost.mp3", Sound.class);
        game.assets.manager.load("data/sounds/player/ghost_end.mp3", Sound.class);
        game.assets.manager.load("data/sounds/player/pickup_item.mp3", Sound.class);
        game.assets.manager.load("data/sounds/player/powerdown.mp3", Sound.class);
        game.assets.manager.load("data/sounds/player/run_stop.mp3", Sound.class);
        game.assets.manager.load("data/sounds/wall_hit.mp3", Sound.class);

        game.assets.manager.load("data/sounds/sprout_1.mp3", Sound.class);

        game.assets.manager.load("data/sounds/enemy/furball/die.mp3", Sound.class);

        game.assets.manager.load("data/music/land/land_5.mp3", Music.class);



        /*FreetypeFontLoader.FreeTypeFontLoaderParameter coinSize = Constants.defaultFontParams;
        coinSize.fontParameters.size = 10;
        coinSize.fontParameters.characters = "0123456789";
        Assets.manager.load("coin.ttf", BitmapFont.class, coinSize);*/

        FreetypeFontLoader.FreeTypeFontLoaderParameter debugFontParams = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        debugFontParams.fontFileName = "data/fonts/MyriadPro-Regular.otf";
        debugFontParams.fontParameters.size = (int) (height / 25f);
        debugFontParams.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS;
        game.assets.manager.load("debug.ttf", BitmapFont.class, debugFontParams);

        debugFontParams = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        debugFontParams.fontFileName = Constants.DEFAULT_FONT_FILE_NAME;
        debugFontParams.fontParameters.size = (int) (height / 40f);
        debugFontParams.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS;
        game.assets.manager.load("debug_object.ttf", BitmapFont.class, debugFontParams);

        exitDialog.loadAssets();

        FreetypeFontLoader.FreeTypeFontLoaderParameter pointsParams = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        pointsParams.fontFileName = Constants.DEFAULT_FONT_FILE_NAME;
        pointsParams.fontParameters.size = (int) HUD.C_H / 35;
        pointsParams.fontParameters.characters = "0123456789";
        game.assets.manager.load("kill-points.ttf", BitmapFont.class, pointsParams);

    }

    private void loadTextures()
    {
        leafEffect = new ParticleEffect();
        leafEffect.load(game.assets.resolver.resolve("data/animation/particles/leaf_emitter.p"), game.assets.resolver.resolve("data/animation/particles"));
        leafEffect.setPosition(Constants.CAMERA_WIDTH / 2, Constants.CAMERA_HEIGHT);
        leafEffect.start();
    }

    @Override
    public void onAssetsLoaded()
    {
        generator.preLoad(cam);
        hud.initAssets();
        audioOn = game.assets.manager.get("data/sounds/audio_on.mp3", Sound.class);
        exitDialog.initAssets();

        debugFont = game.assets.manager.get("debug.ttf");
        debugFont.setColor(1, 0, 0, 1);

        debugObjectFont = game.assets.manager.get("debug_object.ttf");
        debugGlyph = new GlyphLayout();

        for (GameObject go : world.level.gameObjects)
            go.initAssets();

        BitmapFont pointsFont = game.assets.manager.get("kill-points.ttf");
        pointsFont.setColor(1, 1, 1, 1);
        killPointsTextHandler = new KillPointsTextHandler(pointsFont);
        world.level.background.onAssetsLoaded(cam, game.assets);
        world.level.parallaxClouds.onAssetsLoaded(cam);
        world.level.parallaxGround1.onAssetsLoaded(cam);
        world.level.parallaxGround2.onAssetsLoaded(cam);
    }

    // * InputProcessor methods ***************************//

    @Override
    public boolean keyDown(int keycode)
    {
        if (gameState == GAME_STATE.GAME_READY) gameState = GAME_STATE.GAME_RUNNING;
        if (keycode == Input.Keys.LEFT)
        {
            hud.leftPressed();
        }
        if (keycode == Input.Keys.RIGHT)
        {
            hud.rightPressed();
        }
        if (keycode == Input.Keys.SPACE)
        {
            world.maryo.jumpPressed();
            hud.jumpPressed();
        }
        if (keycode == Input.Keys.ALT_LEFT)
        {
            world.maryo.firePressed();
            hud.firePressed();
        }
        if (keycode == Input.Keys.X)
        {
            world.maryo.firePressed();
            hud.firePressed();
        }
        if (keycode == Input.Keys.DOWN)
        {
            world.maryo.downPressed();
            hud.downPressed();
        }
        if (keycode == Input.Keys.UP)
        {
            world.maryo.upPressed();
            hud.upPressed();
        }
        return true;
    }

    @Override
    public boolean keyUp(int keycode)
    {
        if (keycode == Input.Keys.LEFT)
        {
            hud.leftReleased();
        }
        if (keycode == Input.Keys.RIGHT)
        {
            hud.rightReleased();
        }
        if (keycode == Input.Keys.SPACE)
        {
            world.maryo.jumpReleased();
            hud.jumpReleased();
        }
        if (keycode == Input.Keys.ALT_LEFT)
        {
            world.maryo.fireReleased();
            hud.fireReleased();
        }
        if (keycode == Input.Keys.X)
        {
            world.maryo.fireReleased();
            hud.fireReleased();
        }
        if (keycode == Input.Keys.DOWN)
        {
            world.maryo.downReleased();
            hud.downReleased();
        }
        if (keycode == Input.Keys.UP)
        {
            world.maryo.upReleased();
            hud.upReleased();
        }
        if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE)
        {
            if (gameState == GAME_STATE.GAME_PAUSED)
            {
                if (exitDialog.visible) exitDialog.hide();
                else exitDialog.show();
            }
            else
            {
                gameState = GAME_STATE.GAME_PAUSED;
            }
        }
        if (keycode == Input.Keys.D)
            debug = !debug;

        if (keycode == Input.Keys.ENTER)
        {
            hud.boxTextPopup.hide();
            setGameState(GAME_STATE.GAME_RUNNING);
        }
        return true;
    }

    @Override
    public boolean keyTyped(char character)
    {
        return false;
    }

    @Override
    public boolean touchDown(int x, int y, int pointer, int button)
    {
        if (exitDialog.visible)
        {
            exitDialog.touchDown(x, invertY(y));
            return true;
        }
        if (gameState == GAME_STATE.GAME_READY) gameState = GAME_STATE.GAME_RUNNING;
        if (gameState == GAME_STATE.GAME_OVER) goTouched = true;
        if (pointer < 5)
        {
            Vector2 vect = World.VECTOR2_POOL.obtain();
            if (MaryoGame.showOnScreenControls())
            {
                if (Intersector.isPointInPolygon(hud.upPolygon, vect.set(x, invertY(y))))//is top
                {
                    world.maryo.upPressed();
                    touches.get(pointer).clickArea = HUD.Key.up;
                    hud.upPressed();
                }
                if (Intersector.isPointInPolygon(hud.downPolygon, vect.set(x, invertY(y))))//is bottom
                {
                    world.maryo.downPressed();
                    touches.get(pointer).clickArea = HUD.Key.down;
                    hud.downPressed();
                }
                if (hud.jumpRT.contains(x, invertY(y)))
                {
                    world.maryo.jumpPressed();
                    touches.get(pointer).clickArea = HUD.Key.jump;
                    hud.jumpPressed();
                }
                if (hud.fireRT.contains(x, invertY(y)))
                {
                    world.maryo.firePressed();
                    touches.get(pointer).clickArea = HUD.Key.fire;
                    hud.firePressed();
                }
            }
            if (hud.pauseR.contains(x, invertY(y)))
            {
                touches.get(pointer).clickArea = HUD.Key.pause;
                hud.pausePressed();
            }
            if (gameState == GAME_STATE.GAME_PAUSED && hud.soundR.contains(x, invertY(y)))
            {
                touches.get(pointer).clickArea = HUD.Key.sound;
                hud.soundPressed();
            }
            if (gameState == GAME_STATE.GAME_PAUSED && hud.musicR.contains(x, invertY(y)))
            {
                touches.get(pointer).clickArea = HUD.Key.music;
                hud.musicPressed();
            }
            if (gameState == GAME_STATE.GAME_PAUSED && hud.playR.contains(x, invertY(y)))
            {
                touches.get(pointer).clickArea = HUD.Key.play;
                hud.playPressed();
            }
            World.VECTOR2_POOL.free(vect);
        }
        if (hud.boxTextPopup.showing)
        {
            hud.boxTextPopup.hide();
            setGameState(GAME_STATE.GAME_RUNNING);
        }
        return true;
    }

    private float invertY(float y)
    {
        return height - y;
    }

    @Override
    public boolean touchUp(int x, int y, int pointer, int button)
    {
        if (exitDialog.visible)
        {
            exitDialog.touchUp(x, invertY(y));
            return true;
        }
        TouchInfo ti = touches.get(pointer);
        if (ti != null)
        {
            switch (ti.clickArea)
            {
                case up:
                    if (MaryoGame.showOnScreenControls())
                    {
                        world.maryo.upReleased();
                        hud.upReleased();
                    }
                    break;
                case down:
                    if (MaryoGame.showOnScreenControls())
                    {
                        world.maryo.downReleased();
                        hud.downReleased();
                    }
                    break;
                case jump:
                    if (MaryoGame.showOnScreenControls())
                    {
                        world.maryo.jumpReleased();
                        hud.jumpReleased();
                    }
                    break;
                case fire:
                    if (MaryoGame.showOnScreenControls())
                    {
                        world.maryo.fireReleased();
                        hud.fireReleased();
                    }
                    break;
                case pause:
                    if (gameState == GAME_STATE.GAME_RUNNING) gameState = GAME_STATE.GAME_PAUSED;
                    hud.pauseReleased();
                    break;
                case play:
                    gameState = GAME_STATE.GAME_RUNNING;
                    hud.playReleased();
                    break;
                case sound:
                    if (Utility.toggleSound())
                    {
                        SoundManager.play(audioOn);
                    }
                    hud.soundReleased();
                    break;
                case music:
                    if (Utility.toggleMusic())
                    {
                        MusicManager.play(music);
                    }
                    else
                    {
                        music.pause();
                    }
                    hud.musicReleased();
                    break;
            }
            touches.get(pointer).clickArea = HUD.Key.none;
        }
        return true;
    }

    @Override
    public boolean touchDragged(int x, int y, int pointer)
    {
        if (exitDialog.visible)
        {
            exitDialog.touchDragged(x, invertY(y));
            return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(int amount)
    {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY)
    {
        return false;
    }

    public World getWorld()
    {
        return world;
    }

    public void setWorld(World world)
    {
        this.world = world;
    }

    public float getWidth()
    {
        return width;
    }

    public void setWidth(float width)
    {
        this.width = width;
    }

    public float getHeight()
    {
        return height;
    }

    public void setHeight(float height)
    {
        this.height = height;
    }

    class TouchInfo
    {
        public float touchX = 0;
        public float touchY = 0;
        public boolean touched = false;
        HUD.Key clickArea = HUD.Key.none;
    }

    public static class KillPointsTextHandler
    {
        private final List<KillPoint> pointsTextPool = new ArrayList<>(10);
        private BitmapFont font;
        NAHudText<Integer> text = new NAHudText<>(null, null);

        public KillPointsTextHandler(BitmapFont font)
        {
            this.font = font;
            font.getData().setScale(0.01f);
        }

        public void add(int points, float positionX, float positionY)
        {
            for (KillPoint point : pointsTextPool)
            {
                if (point.recycled)
                {
                    point.reset(positionX, positionY, points);
                    return;
                }
            }
            KillPoint point = new KillPoint(points, positionX, positionY);
            pointsTextPool.add(point);
        }

        public void render(SpriteBatch batch, float deltaTime)
        {
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0, size = pointsTextPool.size(); i < size; i++)
            {
                KillPoint point = pointsTextPool.get(i);
                if (!point.recycled)
                {
                    point.draw(batch, deltaTime, font, text);
                }
            }
        }

        private static class KillPoint
        {
            static final float velocity = 0.9f;
            static final float maxDistance = 0.4f;
            private boolean recycled = false;
            private int points;
            private float positionX, positionY, origPosY;
            private float alpha = 1;

            public KillPoint(int points, float positionX, float positionY)
            {
                this.points = points;
                this.positionX = positionX;
                this.positionY = positionY;
                this.origPosY = positionY;
            }

            public void draw(SpriteBatch spriteBatch, float deltaTime, BitmapFont font, NAHudText<Integer> text)
            {
                if (positionY >= origPosY + maxDistance)
                {
                    recycled = true;
                    return;
                }
                float velDelta = velocity * deltaTime;
                positionY += maxDistance * velDelta;
                alpha -= 1 / (maxDistance / (maxDistance * velDelta));
                font.getColor().set(1, 1, 1, alpha);
                font.draw(spriteBatch, text.toString(points), positionX, positionY);
            }

            public void reset(float posX, float posY, int points)
            {
                recycled = false;
                positionX = posX;
                positionY = posY;
                this.points = points;
                origPosY = posY;
                alpha = 1;
            }
        }
    }
}
