package rs.pedjaapps.smc.model;

import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.*;
import rs.pedjaapps.smc.Assets;
import rs.pedjaapps.smc.utility.Utility;

public class Sprite extends GameObject
{
    protected String textureAtlas;
    private String textureName;//name of texture from pack or png
    protected TYPE type = null;

    @Override
    public void render(SpriteBatch spriteBatch)
    {
		TextureRegion region = Assets.loadedRegions.get(textureName);
		if(textureName.contains("flip"))
		{
			//System.out.println("flip");
		}
		//spriteBatch.draw(region, sprite.getPosition().x, sprite.getPosition().y, sprite.getBounds().width, sprite.getBounds().height);
		Utility.draw(spriteBatch, region, position.x, position.y, bounds.height);
        
    }

    @Override
    public void update(float delta)
    {

    }

    @Override
    public void loadTextures()
    {

    }

    /**
     * Type of the block
     * massive = player cant pass by it or stand on it, eg. no collision detection
     * passive = player passes in front of it
     * front_passive = player passes behind it
     * */
    public enum TYPE
    {
        massive, passive, front_passive
    }

    public Sprite(Vector3 position, float width, float height)
    {
        super(new Rectangle(position.x, position.y, width, height), position);
        this.position = position;
    }

    

    public String getTextureAtlas()
    {
        return textureAtlas;
    }

    public void setTextureAtlas(String textureAtlas)
    {
        this.textureAtlas = textureAtlas;
    }

    public String getTextureName()
    {
        return textureName;
    }

    public void setTextureName(String textureName)
    {
        this.textureName = textureName;
    }

    public void setBounds(Rectangle bounds)
    {
        this.bounds = bounds;
    }

    public TYPE getType()
    {
        return type;
    }

    public void setType(TYPE type)
    {
        this.type = type;
    }

    @Override
    public String toString()
    {
        return textureName;
    }
}