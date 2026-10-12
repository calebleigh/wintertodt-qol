package com.wintertodtqol;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Not a unit test: running main() launches a full RuneLite dev client with this plugin loaded.
 * Run it from IntelliJ, or with `./gradlew run`.
 */
public class WintertodtQolPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(WintertodtQolPlugin.class);
		RuneLite.main(args);
	}
}
