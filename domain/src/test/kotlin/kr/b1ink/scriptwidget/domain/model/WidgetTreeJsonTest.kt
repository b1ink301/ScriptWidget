package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetTreeJsonTest {

    @Test
    fun `decodes the tree emitted by prelude js and ignores unknown fields`() {
        val json = """
            {"type":"widget","bg":"#FF1C1C1E","padding":[1,2,3,4],"spacing":4,"url":"https://a.b",
             "children":[
               {"type":"text","text":"Hi","color":"#FFFFFFFF","size":20,"weight":"bold","lineLimit":1,"align":"center"},
               {"type":"spacer","length":null},
               {"type":"stack","dir":"v","children":[
                 {"type":"image","src":"/x.png","width":10,"height":20,"size":[24,24],"cornerRadius":4},
                 {"type":"date","time":1000,"style":"relative"}
               ]},
               {"type":"spacer","length":8,"futureField":true}
             ]}
        """.trimIndent()

        val root = WidgetTree.decode(json)

        assertEquals("#FF1C1C1E", root.bg)
        assertEquals(listOf(1f, 2f, 3f, 4f), root.padding)
        assertEquals(4, root.children.size)
        assertEquals(TextNode(text = "Hi", color = "#FFFFFFFF", size = 20f, weight = "bold", lineLimit = 1, align = "center"), root.children[0])
        assertEquals(SpacerNode(null), root.children[1])
        val stack = root.children[2] as StackNode
        assertEquals("v", stack.dir)
        assertEquals(ImageNode(src = "/x.png", width = 10, height = 20, size = listOf(24f, 24f), cornerRadius = 4f), stack.children[0])
        assertEquals(DateNode(time = 1000, style = "relative"), stack.children[1])
        assertEquals(SpacerNode(8f), root.children[3])
    }

    @Test
    fun `round trips through encode and decode`() {
        val root = WidgetRoot(bg = "#FF000000", children = listOf(TextNode("a"), StackNode(children = listOf(SpacerNode()))))
        assertEquals(root, WidgetTree.decode(WidgetTree.encode(root)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a non-widget root`() {
        WidgetTree.decode("""{"type":"text","text":"not a root"}""")
    }

    @Test
    fun `decodes refreshAfter`() {
        assertEquals(1_700_000_000_000L, WidgetTree.decode("""{"type":"widget","refreshAfter":1700000000000}""").refreshAfter)
    }

    @Test
    fun `decodes gradients and background images on widgets and stacks`() {
        val root = WidgetTree.decode(
            """
            {"type":"widget",
             "gradient":{"colors":["#FF000000","#FFFFFFFF"],"locations":[0,1],"start":[0,0],"end":[1,1]},
             "backgroundImage":"/bg.png",
             "children":[{"type":"stack","gradient":{"colors":["#FF111111","#FF222222"]},"backgroundImage":"/s.png"}]}
            """
        )

        assertEquals(Gradient(listOf("#FF000000", "#FFFFFFFF"), listOf(0f, 1f), listOf(0f, 0f), listOf(1f, 1f)), root.gradient)
        assertEquals("/bg.png", root.backgroundImage)
        val stack = root.children.single() as StackNode
        assertEquals(Gradient(listOf("#FF111111", "#FF222222")), stack.gradient)
        assertEquals("/s.png", stack.backgroundImage)
    }

    @Test
    fun `decodes image alignment, tint, content mode and stack borders`() {
        val root = WidgetTree.decode(
            """
            {"type":"widget","children":[
              {"type":"image","src":"/i.png","align":"right","tint":"#FFFF0000","contentMode":"fill"},
              {"type":"stack","borderColor":"#FF00FF00","borderWidth":2}
            ]}
            """
        )
        assertEquals(ImageNode(src = "/i.png", align = "right", tint = "#FFFF0000", contentMode = "fill"), root.children[0])
        val stack = root.children[1] as StackNode
        assertEquals("#FF00FF00", stack.borderColor)
        assertEquals(2f, stack.borderWidth)
    }

    @Test
    fun `image defaults match Scriptable`() {
        val image = WidgetTree.decode("""{"type":"widget","children":[{"type":"image","src":"/i.png"}]}""").children[0] as ImageNode
        assertEquals("left", image.align)
        assertEquals("fit", image.contentMode)
        assertEquals(null, image.tint)
    }

    @Test
    fun `decodes tap actions on every element`() {
        val root = WidgetTree.decode(
            """
            {"type":"widget","action":"open","children":[
              {"type":"text","text":"+","action":"inc"},
              {"type":"image","src":"/i.png","action":"img"},
              {"type":"stack","action":"row","children":[]},
              {"type":"date","time":1,"action":"date"}
            ]}
            """
        )
        assertEquals("open", root.action)
        assertEquals(listOf("inc", "img", "row", "date"), root.children.map {
            when (it) {
                is TextNode -> it.action
                is ImageNode -> it.action
                is StackNode -> it.action
                is DateNode -> it.action
                else -> null
            }
        })
    }
}
