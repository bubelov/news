package co.appreactor.news.api.standalone

import org.junit.Assert.assertEquals
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class DocumentExtensionsTests {

    @Test
    fun getFeedType_recognizesAtom() {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = builder.parse(ATOM_FEED_1.byteInputStream())
        assertEquals(FeedType.ATOM, document.getFeedType())
    }

    @Test
    fun getFeedType_recognizesRss() {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = builder.parse(RSS_FEED_1.byteInputStream())
        assertEquals(FeedType.RSS, document.getFeedType())
    }

    @Test
    fun toAtomFeed() {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        ATOM_FEED_1.byteInputStream().apply {
            val document = builder.parse(this)
            val feed = document.toAtomFeed()
            assertEquals("tag:github.com,2008:https://github.com/curl/curl/releases", feed.id)
            assertEquals("Release notes from curl", feed.title)
            assertEquals("https://github.com/curl/curl/releases.atom", feed.selfLink)
            assertEquals("https://github.com/curl/curl/releases", feed.alternateLink)
        }

        ATOM_FEED_2.byteInputStream().apply {
            val document = builder.parse(this)
            val feed = document.toAtomFeed()
            assertEquals("https://www.kernel.org/", feed.id)
            assertEquals("The Linux Kernel Archives", feed.title)
            assertEquals("https://www.kernel.org/feeds/all.atom.xml", feed.selfLink)
            assertEquals("https://www.kernel.org/", feed.alternateLink)
        }
    }

    companion object {
        const val ATOM_FEED_1 = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/" xml:lang="en-US">
  <id>tag:github.com,2008:https://github.com/curl/curl/releases</id>
  <link type="text/html" rel="alternate" href="https://github.com/curl/curl/releases"/>
  <link type="application/atom+xml" rel="self" href="https://github.com/curl/curl/releases.atom"/>
  <title>Release notes from curl</title>
  <updated>2020-08-27T15:20:02+07:00</updated>
  <entry>
    <id>tag:github.com,2008:Repository/569041/tiny-curl-7_72_0</id>
    <updated>2020-08-27T15:20:02+07:00</updated>
    <link rel="alternate" type="text/html" href="https://github.com/curl/curl/releases/tag/tiny-curl-7_72_0"/>
    <title>tiny-curl-7_72_0</title>
    <content type="html">&lt;p&gt;tiny-curl 7.72.0&lt;/p&gt;</content>
    <author>
      <name>bagder</name>
    </author>
    <media:thumbnail height="30" width="30" url="https://avatars2.githubusercontent.com/u/177011?s=60&amp;v=4"/>
  </entry>
  <entry>
    <id>tag:github.com,2008:Repository/569041/curl-7_72_0</id>
    <updated>2020-08-19T14:47:58+07:00</updated>
    <link rel="alternate" type="text/html" href="https://github.com/curl/curl/releases/tag/curl-7_72_0"/>
    <title>curl-7_72_0</title>
    <content type="html">&lt;p&gt;&lt;a href=&quot;https://curl.haxx.se/changes.html#7_72_0&quot; rel=&quot;nofollow&quot;&gt;changelog&lt;/a&gt;&lt;/p&gt;</content>
    <author>
      <name>bagder</name>
    </author>
    <media:thumbnail height="30" width="30" url="https://avatars2.githubusercontent.com/u/177011?s=60&amp;v=4"/>
  </entry>
  <entry>
    <id>tag:github.com,2008:Repository/569041/curl-7_71_1</id>
    <updated>2020-07-01T13:50:45+07:00</updated>
    <link rel="alternate" type="text/html" href="https://github.com/curl/curl/releases/tag/curl-7_71_1"/>
    <title>7.71.1</title>
    <content type="html">&lt;p&gt;&lt;a href=&quot;https://curl.haxx.se/changes.html#7_71_1&quot; rel=&quot;nofollow&quot;&gt;changelog&lt;/a&gt;&lt;/p&gt;</content>
    <author>
      <name>bagder</name>
    </author>
    <media:thumbnail height="30" width="30" url="https://avatars2.githubusercontent.com/u/177011?s=60&amp;v=4"/>
  </entry>
  <entry>
    <id>tag:github.com,2008:Repository/569041/curl-7_71_0</id>
    <updated>2020-06-24T13:53:12+07:00</updated>
    <link rel="alternate" type="text/html" href="https://github.com/curl/curl/releases/tag/curl-7_71_0"/>
    <title>7.71.0</title>
    <content type="html">&lt;p&gt;&lt;a href=&quot;https://curl.haxx.se/changes.html#7_71_0&quot; rel=&quot;nofollow&quot;&gt;changelog&lt;/a&gt;&lt;/p&gt;</content>
    <author>
      <name>bagder</name>
    </author>
    <media:thumbnail height="30" width="30" url="https://avatars2.githubusercontent.com/u/177011?s=60&amp;v=4"/>
  </entry>
  <entry>
    <id>tag:github.com,2008:Repository/569041/curl-7_70_0</id>
    <updated>2020-04-29T16:48:39+07:00</updated>
    <link rel="alternate" type="text/html" href="https://github.com/curl/curl/releases/tag/curl-7_70_0"/>
    <title>7.70.0</title>
    <content type="html">&lt;p&gt;&lt;a href=&quot;https://curl.haxx.se/changes.html#7_70_0&quot; rel=&quot;nofollow&quot;&gt;changelog&lt;/a&gt;&lt;/p&gt;</content>
    <author>
      <name>bagder</name>
    </author>
    <media:thumbnail height="30" width="30" url="https://avatars2.githubusercontent.com/u/177011?s=60&amp;v=4"/>
  </entry>
  <entry>
    <id>tag:github.com,2008:Repository/569041/curl-7_69_1</id>
    <updated>2020-03-11T13:43:44+07:00</updated>
    <link rel="alternate" type="text/html" href="https://github.com/curl/curl/releases/tag/curl-7_69_1"/>
    <title>7.69.1</title>
    <content type="html">&lt;p&gt;&lt;a href=&quot;https://curl.haxx.se/changes.html#7_69_1&quot; rel=&quot;nofollow&quot;&gt;changelog&lt;/a&gt;&lt;/p&gt;</content>
    <author>
      <name>bagder</name>
    </author>
    <media:thumbnail height="30" width="30" url="https://avatars2.githubusercontent.com/u/177011?s=60&amp;v=4"/>
  </entry>
  <entry>
    <id>tag:github.com,2008:Repository/569041/curl-7_69_0</id>
    <updated>2020-03-04T13:48:56+07:00</updated>
    <link rel="alternate" type="text/html" href="https://github.com/curl/curl/releases/tag/curl-7_69_0"/>
    <title>7.69.0</title>
    <content type="html">&lt;p&gt;&lt;a href=&quot;https://curl.haxx.se/changes.html#7_69_0&quot; rel=&quot;nofollow&quot;&gt;changelog&lt;/a&gt;&lt;/p&gt;</content>
    <author>
      <name>bagder</name>
    </author>
    <media:thumbnail height="30" width="30" url="https://avatars2.githubusercontent.com/u/177011?s=60&amp;v=4"/>
  </entry>
  <entry>
    <id>tag:github.com,2008:Repository/569041/curl-7_68_0</id>
    <updated>2020-01-08T13:40:18+07:00</updated>
    <link rel="alternate" type="text/html" href="https://github.com/curl/curl/releases/tag/curl-7_68_0"/>
    <title>7.68.0</title>
    <content type="html">&lt;p&gt;&lt;a href=&quot;https://curl.haxx.se/changes.html#7_68_0&quot; rel=&quot;nofollow&quot;&gt;changelog&lt;/a&gt;&lt;/p&gt;</content>
    <author>
      <name>bagder</name>
    </author>
    <media:thumbnail height="30" width="30" url="https://avatars2.githubusercontent.com/u/177011?s=60&amp;v=4"/>
  </entry>
  <entry>
    <id>tag:github.com,2008:Repository/569041/curl-7_67_0</id>
    <updated>2019-11-06T14:25:12+07:00</updated>
    <link rel="alternate" type="text/html" href="https://github.com/curl/curl/releases/tag/curl-7_67_0"/>
    <title>7.67.0</title>
    <content type="html">&lt;p&gt;&lt;a href=&quot;https://curl.haxx.se/changes.html#7_67_0&quot; rel=&quot;nofollow&quot;&gt;changelog&lt;/a&gt;&lt;/p&gt;</content>
    <author>
      <name>bagder</name>
    </author>
    <media:thumbnail height="30" width="30" url="https://avatars2.githubusercontent.com/u/177011?s=60&amp;v=4"/>
  </entry>
  <entry>
    <id>tag:github.com,2008:Repository/569041/curl-7_66_0</id>
    <updated>2019-09-11T12:56:24+07:00</updated>
    <link rel="alternate" type="text/html" href="https://github.com/curl/curl/releases/tag/curl-7_66_0"/>
    <title>7.66.0</title>
    <content type="html">&lt;p&gt;&lt;a href=&quot;https://curl.haxx.se/changes.html#7_66_0&quot; rel=&quot;nofollow&quot;&gt;changelog&lt;/a&gt;&lt;/p&gt;</content>
    <author>
      <name>bagder</name>
    </author>
    <media:thumbnail height="30" width="30" url="https://avatars2.githubusercontent.com/u/177011?s=60&amp;v=4"/>
  </entry>
</feed>
        """

        const val ATOM_FEED_2 = """<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom"><title>The Linux Kernel Archives</title><link href="https://www.kernel.org/" rel="alternate"></link><link href="https://www.kernel.org/feeds/all.atom.xml" rel="self"></link><id>https://www.kernel.org/</id><updated>2020-07-02T16:50:07+00:00</updated><entry><title>Active kernel releases</title><link href="https://www.kernel.org/releases.html" rel="alternate"></link><published>2020-07-02T16:50:07+00:00</published><updated>2020-07-02T16:50:07+00:00</updated><author><name></name></author><id>tag:https://www.kernel.org,2020-07-02:releases.html</id><summary type="html">&lt;p&gt;There are several main categories into which kernel releases may fall:&lt;/p&gt;
&lt;dl class="docutils"&gt;
&lt;dt&gt;Prepatch&lt;/dt&gt;
&lt;dd&gt;Prepatch or &amp;quot;RC&amp;quot; kernels are mainline kernel pre-releases that are
mostly aimed at other kernel developers and Linux enthusiasts. They
must be compiled from source and usually contain new features that
must be tested before they can be put into a stable release.
Prepatch kernels are maintained and released by Linus Torvalds.&lt;/dd&gt;
&lt;dt&gt;Mainline&lt;/dt&gt;
&lt;dd&gt;Mainline tree is maintained by Linus Torvalds. It's the tree where
all new features are introduced and where all the exciting new
development happens. New mainline kernels are released every 2-3
months.&lt;/dd&gt;
&lt;dt&gt;Stable&lt;/dt&gt;
&lt;dd&gt;After each mainline kernel is released, it is considered &amp;quot;stable.&amp;quot;
Any bug fixes for a stable kernel are backported from the mainline
tree and applied by a designated stable kernel maintainer. There are
usually only a few bugfix kernel releases until next mainline kernel
becomes available -- unless it is designated a &amp;quot;longterm maintenance
kernel.&amp;quot; Stable kernel updates are released on as-needed basis,
usually once a week.&lt;/dd&gt;
&lt;dt&gt;Longterm&lt;/dt&gt;
&lt;dd&gt;There are usually several &amp;quot;longterm maintenance&amp;quot; kernel releases
provided for the purposes of backporting bugfixes for older kernel
trees. Only important bugfixes are applied to such kernels and they
don't usually see very frequent releases, especially for older
trees.&lt;/dd&gt;
&lt;/dl&gt;
&lt;table border="1" class="docutils"&gt;
&lt;caption&gt;Longterm release kernels&lt;/caption&gt;
&lt;colgroup&gt;
&lt;col width="11%" /&gt;
&lt;col width="46%" /&gt;
&lt;col width="17%" /&gt;
&lt;col width="26%" /&gt;
&lt;/colgroup&gt;
&lt;thead valign="bottom"&gt;
&lt;tr&gt;&lt;th class="head"&gt;Version&lt;/th&gt;
&lt;th class="head"&gt;Maintainer&lt;/th&gt;
&lt;th class="head"&gt;Released&lt;/th&gt;
&lt;th class="head"&gt;Projected EOL&lt;/th&gt;
&lt;/tr&gt;
&lt;/thead&gt;
&lt;tbody valign="top"&gt;
&lt;tr&gt;&lt;td&gt;5.4&lt;/td&gt;
&lt;td&gt;Greg Kroah-Hartman &amp;amp; Sasha Levin&lt;/td&gt;
&lt;td&gt;2019-11-24&lt;/td&gt;
&lt;td&gt;Dec, 2025&lt;/td&gt;
&lt;/tr&gt;
&lt;tr&gt;&lt;td&gt;4.19&lt;/td&gt;
&lt;td&gt;Greg Kroah-Hartman &amp;amp; Sasha Levin&lt;/td&gt;
&lt;td&gt;2018-10-22&lt;/td&gt;
&lt;td&gt;Dec, 2024&lt;/td&gt;
&lt;/tr&gt;
&lt;tr&gt;&lt;td&gt;4.14&lt;/td&gt;
&lt;td&gt;Greg Kroah-Hartman &amp;amp; Sasha Levin&lt;/td&gt;
&lt;td&gt;2017-11-12&lt;/td&gt;
&lt;td&gt;Jan, 2024&lt;/td&gt;
&lt;/tr&gt;
&lt;tr&gt;&lt;td&gt;4.9&lt;/td&gt;
&lt;td&gt;Greg Kroah-Hartman &amp;amp; Sasha Levin&lt;/td&gt;
&lt;td&gt;2016-12-11&lt;/td&gt;
&lt;td&gt;Jan, 2023&lt;/td&gt;
&lt;/tr&gt;
&lt;tr&gt;&lt;td&gt;4.4&lt;/td&gt;
&lt;td&gt;Greg Kroah-Hartman &amp;amp; Sasha Levin&lt;/td&gt;
&lt;td&gt;2016-01-10&lt;/td&gt;
&lt;td&gt;Feb, 2022&lt;/td&gt;
&lt;/tr&gt;
&lt;/tbody&gt;
&lt;/table&gt;
&lt;div class="section" id="distribution-kernels"&gt;
&lt;h2&gt;Distribution kernels&lt;/h2&gt;
&lt;p&gt;Many Linux distributions provide their own &amp;quot;longterm maintenance&amp;quot;
kernels that may or may not be based on those maintained by kernel
developers. These kernel releases are not hosted at kernel.org and
kernel developers can provide no support for them.&lt;/p&gt;
&lt;p&gt;It is easy to tell if you are running a distribution kernel. Unless you
downloaded, compiled and installed your own version of kernel from
kernel.org, you are running a distribution kernel. To find out the
version of your kernel, run &lt;cite&gt;uname -r&lt;/cite&gt;:&lt;/p&gt;
&lt;pre class="literal-block"&gt;
# uname -r
5.6.19-300.fc32.x86_64
&lt;/pre&gt;
&lt;p&gt;If you see anything at all after the dash, you are running a distribution
kernel. Please use the support channels offered by your distribution
vendor to obtain kernel support.&lt;/p&gt;
&lt;/div&gt;
</summary></entry><entry><title>Git mirror available in Beijing</title><link href="https://www.kernel.org/beijing-git-mirror.html" rel="alternate"></link><published>2020-01-11T00:00:00+00:00</published><updated>2020-01-11T00:00:00+00:00</updated><author><name>Konstantin Ryabitsev</name></author><id>tag:https://www.kernel.org,2020-01-11:beijing-git-mirror.html</id><summary type="html">&lt;p&gt;If you are a developer located around Beijing, or if your connection to
Beijing is faster and more reliable than to locations outside of China,
then you may benefit from the new git.kernel.org mirror kindly provided
by &lt;a class="reference external" href="https://www.codeaurora.org/"&gt;Code Aurora Forum&lt;/a&gt; at &lt;a class="reference external" href="https://kernel.source.codeaurora.cn/"&gt;https://kernel.source.codeaurora.cn/&lt;/a&gt;. This is
a full mirror that is updated just as frequently as other git.kernel.org
nodes (in fact, it is managed by the same team as the rest of kernel.org
infrastructure, since CAF is part of Linux Foundation IT projects).&lt;/p&gt;
&lt;p&gt;To start using the Beijing mirror, simply clone from that location or
add a separate remote to your existing checkouts, e.g.:&lt;/p&gt;
&lt;pre class="literal-block"&gt;
git remote add beijing git://kernel.source.codeaurora.cn/pub/scm/.../linux.git
git fetch beijing master
&lt;/pre&gt;
&lt;p&gt;You may also use &lt;a class="reference external" href="http://"&gt;http://&lt;/a&gt; and &lt;a class="reference external" href="https://"&gt;https://&lt;/a&gt; protocols if that makes it easier
behind corporate firewalls.&lt;/p&gt;
</summary></entry><entry><title>Code of Conduct</title><link href="https://www.kernel.org/code-of-conduct.html" rel="alternate"></link><published>2020-01-02T00:00:00+00:00</published><updated>2020-01-02T00:00:00+00:00</updated><author><name></name></author><id>tag:https://www.kernel.org,2020-01-02:code-of-conduct.html</id><summary type="html">&lt;p&gt;The Linux kernel community operates a &lt;a class="reference external" href="https://www.kernel.org/doc/html/latest/process/code-of-conduct.html"&gt;Code of Conduct&lt;/a&gt; based on the
&lt;a class="reference external" href="https://www.contributor-covenant.org/version/1/4/code-of-conduct.html"&gt;Contributor Covenant Code of Conduct&lt;/a&gt;.&lt;/p&gt;
&lt;div class="section" id="code-of-conduct-committee"&gt;
&lt;h2&gt;Code of Conduct Committee&lt;/h2&gt;
&lt;p&gt;The Linux kernel Code of Conduct Committee is currently made up of the
following people:&lt;/p&gt;
&lt;blockquote&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;Kristen Accardi &amp;lt;&lt;a class="reference external" href="mailto:kristen.c.accardi&amp;#64;intel.com"&gt;kristen.c.accardi&amp;#64;intel.com&lt;/a&gt;&amp;gt;&lt;/li&gt;
&lt;li&gt;Mishi Choudhary &amp;lt;&lt;a class="reference external" href="mailto:mishi&amp;#64;linux.com"&gt;mishi&amp;#64;linux.com&lt;/a&gt;&amp;gt;&lt;/li&gt;
&lt;li&gt;Shuah Khan &amp;lt;&lt;a class="reference external" href="mailto:skhan&amp;#64;linuxfoundation.org"&gt;skhan&amp;#64;linuxfoundation.org&lt;/a&gt;&amp;gt;&lt;/li&gt;
&lt;li&gt;Greg Kroah-Hartman &amp;lt;&lt;a class="reference external" href="mailto:gregkh&amp;#64;linuxfoundation.org"&gt;gregkh&amp;#64;linuxfoundation.org&lt;/a&gt;&amp;gt;&lt;/li&gt;
&lt;/ul&gt;
&lt;/blockquote&gt;
&lt;p&gt;Committee members can be reached all at once by writing to
&amp;lt;&lt;a class="reference external" href="mailto:conduct&amp;#64;kernel.org"&gt;conduct&amp;#64;kernel.org&lt;/a&gt;&amp;gt;.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="committee-reports"&gt;
&lt;h2&gt;Committee Reports&lt;/h2&gt;
&lt;p&gt;We would like to thank the Linux kernel community members who have supported
the adoption of the Code of Conduct and who continue to uphold the professional
standards of our community. If you have any questions about these reports,
please write to &amp;lt;&lt;a class="reference external" href="mailto:conduct&amp;#64;kernel.org"&gt;conduct&amp;#64;kernel.org&lt;/a&gt;&amp;gt;.&lt;/p&gt;
&lt;div class="section" id="december-2019"&gt;
&lt;h3&gt;December 2019&lt;/h3&gt;
&lt;p&gt;Archival copy: &lt;a class="reference external" href="https://lore.kernel.org/lkml/20200103105614.GC1047442&amp;#64;kroah.com/"&gt;https://lore.kernel.org/lkml/20200103105614.GC1047442&amp;#64;kroah.com/&lt;/a&gt;&lt;/p&gt;
&lt;p&gt;In the period of December 1, 2019 through December 30, 2019 the Committee
received the following report:&lt;/p&gt;
&lt;blockquote&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;Insulting behavior in email: 1&lt;/li&gt;
&lt;/ul&gt;
&lt;/blockquote&gt;
&lt;p&gt;The result of the investigation:&lt;/p&gt;
&lt;blockquote&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;Education and coaching: 1&lt;/li&gt;
&lt;/ul&gt;
&lt;/blockquote&gt;
&lt;/div&gt;
&lt;div class="section" id="august-to-november-2019"&gt;
&lt;h3&gt;August to November 2019&lt;/h3&gt;
&lt;p&gt;Archival copy: &lt;a class="reference external" href="https://lore.kernel.org/lkml/20191218090054.GA5120&amp;#64;kroah.com/"&gt;https://lore.kernel.org/lkml/20191218090054.GA5120&amp;#64;kroah.com/&lt;/a&gt;&lt;/p&gt;
&lt;p&gt;In the period of August 1, 2019 through November 31, 2019, the Committee
received no reports.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="september-2018-to-july-2019"&gt;
&lt;h3&gt;September 2018 to July 2019&lt;/h3&gt;
&lt;p&gt;Archival copy: &lt;a class="reference external" href="https://lore.kernel.org/lkml/20190810120700.GA7360&amp;#64;kroah.com/"&gt;https://lore.kernel.org/lkml/20190810120700.GA7360&amp;#64;kroah.com/&lt;/a&gt;&lt;/p&gt;
&lt;p&gt;In the period of September 15, 2018 through July 31, 2019, the Committee
received the following reports:&lt;/p&gt;
&lt;blockquote&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;Inappropriate language in the kernel source: 1&lt;/li&gt;
&lt;li&gt;Insulting behavior in email: 3&lt;/li&gt;
&lt;/ul&gt;
&lt;/blockquote&gt;
&lt;p&gt;The result of the investigations:&lt;/p&gt;
&lt;blockquote&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;Education and coaching: 4&lt;/li&gt;
&lt;/ul&gt;
&lt;/blockquote&gt;
&lt;/div&gt;
&lt;/div&gt;
</summary></entry><entry><title>Frequently asked questions</title><link href="https://www.kernel.org/faq.html" rel="alternate"></link><published>2019-05-02T13:53:08+00:00</published><updated>2019-05-02T13:53:08+00:00</updated><author><name></name></author><id>tag:https://www.kernel.org,2019-05-02:faq.html</id><summary type="html">&lt;p&gt;If you have questions, comments or concerns about the F.A.Q. please
contact us at &lt;a class="reference external" href="mailto:webmaster&amp;#64;kernel.org"&gt;webmaster&amp;#64;kernel.org&lt;/a&gt;.&lt;/p&gt;
&lt;div class="section" id="is-linux-kernel-free-software"&gt;
&lt;h2&gt;Is Linux Kernel Free Software?&lt;/h2&gt;
&lt;p&gt;Linux kernel is released under GNU GPL version 2 and is therefore Free
Software as defined by the &lt;a class="reference external" href="https://www.fsf.org/"&gt;Free Software Foundation&lt;/a&gt;. You may read the
entire copy of the license in the &lt;a class="reference external" href="/pub/linux/kernel/COPYING"&gt;COPYING&lt;/a&gt; file distributed with each
release of the Linux kernel.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="what-does-stable-eol-and-longterm-mean"&gt;
&lt;h2&gt;What does &amp;quot;stable/EOL&amp;quot; and &amp;quot;longterm&amp;quot; mean?&lt;/h2&gt;
&lt;p&gt;As kernels move from the &amp;quot;mainline&amp;quot; into the &amp;quot;stable&amp;quot; category, two
things can happen:&lt;/p&gt;
&lt;ol class="arabic simple"&gt;
&lt;li&gt;They can reach &amp;quot;End of Life&amp;quot; after a few bugfix revisions, which
means that kernel maintainers will release no more bugfixes for this
kernel version, or&lt;/li&gt;
&lt;li&gt;They can be put into &amp;quot;longterm&amp;quot; maintenance, which means that
maintainers will provide bugfixes for this kernel revision for a
much longer period of time.&lt;/li&gt;
&lt;/ol&gt;
&lt;p&gt;If the kernel version you are using is marked &amp;quot;EOL,&amp;quot; you should consider
upgrading to the next major version as there will be no more bugfixes
provided for the kernel version you are using.&lt;/p&gt;
&lt;p&gt;Please check the &lt;a class="reference external" href="https://www.kernel.org/releases.html"&gt;Releases&lt;/a&gt; page for more info.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="why-is-an-lts-kernel-marked-as-stable-on-the-front-page"&gt;
&lt;h2&gt;Why is an LTS kernel marked as &amp;quot;stable&amp;quot; on the front page?&lt;/h2&gt;
&lt;p&gt;Long-term support (&amp;quot;LTS&amp;quot;) kernels announced on the &lt;a class="reference external" href="https://www.kernel.org/releases.html"&gt;Releases&lt;/a&gt; page will
be marked as &amp;quot;stable&amp;quot; on the front page if there are no other current
stable kernel releases. This is done to avoid breaking automated parsers
monitoring kernel.org with an expectation that there will always be a
kernel release marked as &amp;quot;stable.&amp;quot;&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="linus-has-tagged-a-new-release-but-it-s-not-listed-on-the-front-page"&gt;
&lt;h2&gt;Linus has tagged a new release, but it's not listed on the front page!&lt;/h2&gt;
&lt;p&gt;Linus Torvalds PGP-signs git repository tags for all new mainline kernel
releases, however a separate set of PGP signatures needs to be generated
by the stable release team in order to create downloadable tarballs. Due
to timezone differences between Linus and the members of the stable
team, there is usually a delay of several hours between when the new
mainline release is tagged and when PGP-signed tarballs become
available. The front page is updated once that process is completed.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="is-there-an-rss-feed-for-the-latest-kernel-version"&gt;
&lt;h2&gt;Is there an RSS feed for the latest kernel version?&lt;/h2&gt;
&lt;p&gt;Yes, and you can find it at &lt;a class="reference external" href="https://www.kernel.org/feeds/kdist.xml"&gt;https://www.kernel.org/feeds/kdist.xml&lt;/a&gt;.&lt;/p&gt;
&lt;p&gt;We also publish a .json file with the latest release information, which
you can pull from here: &lt;a class="reference external" href="https://www.kernel.org/releases.json"&gt;https://www.kernel.org/releases.json&lt;/a&gt;.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="why-are-there-files-that-are-dated-tomorrow"&gt;
&lt;h2&gt;Why are there files that are dated tomorrow?&lt;/h2&gt;
&lt;p&gt;All timestamps on kernel.org are in UTC (Coordinated Universal Time). If
you live in the western hemisphere your local time lags behind UTC.
Under Linux/Unix, type &lt;tt class="docutils literal"&gt;date &lt;span class="pre"&gt;-u&lt;/span&gt;&lt;/tt&gt; to get the current time in UTC.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="can-i-get-an-account-on-kernel-org"&gt;
&lt;h2&gt;Can I get an account on kernel.org?&lt;/h2&gt;
&lt;p&gt;Kernel.org accounts are usually reserved for subsystem maintainers or
high-profile developers. It is absolutely not necessary to have an
account on kernel.org to contribute to the development of the Linux
kernel, unless you submit pull requests directly to Linus.&lt;/p&gt;
&lt;p&gt;If you are listed in the MAINTAINERS file or have reasons to believe you
should have an account on kernel.org because of the amount of your
contributions, please refer to the &lt;a class="reference external" href="https://korg.wiki.kernel.org/userdoc/accounts"&gt;accounts wiki page&lt;/a&gt; for the
procedure to follow.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="i-have-cool-project-x-can-you-guys-mirror-it-for-me"&gt;
&lt;h2&gt;I have cool project X, can you guys mirror it for me?&lt;/h2&gt;
&lt;p&gt;Probably not. Kernel.org deals with the Linux kernel, various
distributions of the kernel and larger repositories of packages. We do
not mirror individual projects, software, etc as we feel there are
better places providing mirrors for those kinds of repositories. If you
feel that kernel.org should mirror your project, please contact
&lt;a class="reference external" href="mailto:ftpadmin&amp;#64;kernel.org"&gt;ftpadmin&amp;#64;kernel.org&lt;/a&gt; with the following information:&lt;/p&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;name&lt;/li&gt;
&lt;li&gt;project name&lt;/li&gt;
&lt;li&gt;project website&lt;/li&gt;
&lt;li&gt;detailed project description&lt;/li&gt;
&lt;li&gt;reason for wanting us to mirror&lt;/li&gt;
&lt;/ul&gt;
&lt;p&gt;The Kernel.org admin team will then review your request and talk to you
about it. As with any kind of account on kernel.org it's up to the
discretion of the admin team.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="how-does-kernel-org-provide-its-users-access-to-the-git-trees"&gt;
&lt;h2&gt;How does kernel.org provide its users access to the git trees?&lt;/h2&gt;
&lt;p&gt;We are using an access control system called &lt;a class="reference external" href="https://github.com/sitaramc/gitolite/wiki"&gt;gitolite&lt;/a&gt;, originally
written and maintained by Sitaram Chamarty. We chose gitolite for a
number of reasons:&lt;/p&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;Limiting of ssh access to the system&lt;/li&gt;
&lt;li&gt;Fine grained control over repository access&lt;/li&gt;
&lt;li&gt;Well maintained and supported code base&lt;/li&gt;
&lt;li&gt;Responsive development&lt;/li&gt;
&lt;li&gt;Broad and diverse install base&lt;/li&gt;
&lt;/ul&gt;
&lt;p&gt;As well at the time of deployment the code had undergone an external
code review.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="how-do-i-create-an-rc-kernel-i-get-reversed-patch-detected"&gt;
&lt;h2&gt;How do I create an -rc kernel? I get &amp;quot;Reversed patch detected!&amp;quot;&lt;/h2&gt;
&lt;p&gt;-rc kernel patches are generated from the base stable release.&lt;/p&gt;
&lt;p&gt;For example: to create the 2.6.14-rc5 kernel, you must:&lt;/p&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;download 2.6.13 (not 2.6.13.4)&lt;/li&gt;
&lt;li&gt;and then apply the 2.6.14-rc5 patch.&lt;/li&gt;
&lt;/ul&gt;
&lt;p&gt;Yes, you want 2.6.13, not 2.6.14. Remember, that's an -rc kernel, as in, 2.6.14 doesn't exist yet. :)&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="where-can-i-find-kernel-2-4-20-3-16"&gt;
&lt;h2&gt;Where can I find kernel 2.4.20-3.16?&lt;/h2&gt;
&lt;p&gt;Kernel version numbers of this form are distribution kernels, meaning
they are modified kernels produced by distributions. Please contact the
relevant distributor; or check out &lt;a class="reference external" href="https://mirrors.kernel.org/"&gt;https://mirrors.kernel.org/&lt;/a&gt;.&lt;/p&gt;
&lt;p&gt;See the &lt;a class="reference external" href="https://www.kernel.org/releases.html"&gt;Releases&lt;/a&gt; page for more info on distribution kernels.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="i-need-help-building-patching-fixing-linux-kernel-modules-drivers"&gt;
&lt;h2&gt;I need help building/patching/fixing Linux kernel/modules/drivers!&lt;/h2&gt;
&lt;p&gt;Please see the &lt;a class="reference external" href="http://kernelnewbies.org/"&gt;Kernel Newbies&lt;/a&gt; website.&lt;/p&gt;
&lt;p&gt;There is also a wealth of knowledge on many topics involving Linux at
The Linux Documentation Project (&lt;a class="reference external" href="http://www.tldp.org"&gt;http://www.tldp.org&lt;/a&gt;)&lt;/p&gt;
&lt;p&gt;For finding or reporting bugs, look through the archives for the various
Linux mailing lists, and if no specific list seems appropriate, try the
browsing the Linux Kernel Mailing List.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="what-happened-to-ftp-kernel-org"&gt;
&lt;h2&gt;What happened to ftp.kernel.org?&lt;/h2&gt;
&lt;p&gt;FTP service was terminated on March 1, 2017. All content that used to be
available via ftp.kernel.org can be accessed by browsing
&lt;a class="reference external" href="https://www.kernel.org/pub/"&gt;https://www.kernel.org/pub/&lt;/a&gt;. If you would like to use a command-line
tool for accessing these files, you can do so with lftp:&lt;/p&gt;
&lt;blockquote&gt;
lftp &lt;a class="reference external" href="https://www.kernel.org/pub"&gt;https://www.kernel.org/pub&lt;/a&gt;&lt;/blockquote&gt;
&lt;/div&gt;
&lt;div class="section" id="when-will-the-next-kernel-be-released"&gt;
&lt;h2&gt;When will the next kernel be released?&lt;/h2&gt;
&lt;p&gt;The next kernel will be released when it is ready. There is no strict
timeline for making releases, but if you really need an educated guess,
visit the Linux kernel &lt;a class="reference external" href="http://phb-crystal-ball.org/"&gt;PHB Crystal Ball&lt;/a&gt; -- it tries to provide a
ballpark guess based on previous kernel release schedule.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="what-will-go-into-the-next-release"&gt;
&lt;h2&gt;What will go into the next release?&lt;/h2&gt;
&lt;p&gt;It is hard to predict with certainty, but you can either take a peek at
&lt;a class="reference external" href="https://git.kernel.org/pub/scm/linux/kernel/git/next/linux-next.git/"&gt;linux-next&lt;/a&gt; or read the &lt;a class="reference external" href="https://www.linux.com/news/2017/7/linux-weather-forecast"&gt;Linux Weather Forecast&lt;/a&gt;, where Jonathan
Corbet provides a broad forecast of what will likely be included into
the next mainline release.&lt;/p&gt;
&lt;/div&gt;
</summary></entry><entry><title>Get notifications for your patches</title><link href="https://www.kernel.org/get-notifications-for-your-patches.html" rel="alternate"></link><published>2018-12-13T00:00:00+00:00</published><updated>2018-12-13T00:00:00+00:00</updated><author><name></name></author><id>tag:https://www.kernel.org,2018-12-13:get-notifications-for-your-patches.html</id><summary type="html">&lt;p&gt;We are trialing out a new feature that can send you a notification when
the patches you send to the LKML are applied to linux-next or to the
mainline git trees. If you are interested in trying it out, here are the
details:&lt;/p&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;The patches must be sent to the LKML (&lt;a class="reference external" href="mailto:linux-kernel&amp;#64;vger.kernel.org"&gt;linux-kernel&amp;#64;vger.kernel.org&lt;/a&gt;).&lt;/li&gt;
&lt;li&gt;One of the cc's must be &lt;a class="reference external" href="mailto:notify&amp;#64;kernel.org"&gt;notify&amp;#64;kernel.org&lt;/a&gt; (Bcc will not work).&lt;/li&gt;
&lt;li&gt;Alternatively, there should be a &amp;quot;X-Patchwork-Bot: notify&amp;quot; email header.&lt;/li&gt;
&lt;li&gt;The patches must not have been modified by the maintainer(s).&lt;/li&gt;
&lt;li&gt;All patches in the series must have been applied, not just some of them.&lt;/li&gt;
&lt;/ul&gt;
&lt;p&gt;The last two points are important, because if there are changes between
the content of the patch as it was first sent to the mailing list, and
how it looks like by the time it is applied to linux-next or mainline,
the bot will not be able to recognize it as the same patch. Similarly,
for series of multiple patches, the bot must be able to successfully
match all patches in the series in order for the notification to go out.&lt;/p&gt;
&lt;p&gt;If you are using &lt;tt class="docutils literal"&gt;&lt;span class="pre"&gt;git-format-patch&lt;/span&gt;&lt;/tt&gt;, it is best to add the special
header instead of using the Cc notification address, so as to avoid any
unnecessary email traffic:&lt;/p&gt;
&lt;pre class="literal-block"&gt;
--add-header=&amp;quot;X-Patchwork-Bot: notify&amp;quot;
&lt;/pre&gt;
&lt;p&gt;You should receive one notification email per each patch series, so if
you send a series of 20 patches, you will get a single email in the form
of a reply to the cover letter, or to the first patch in the series. The
notification will be sent directly to you, ignoring any other addresses
in the Cc field.&lt;/p&gt;
&lt;p&gt;The bot uses our &lt;a class="reference external" href="https://lore.kernel.org/patchwork"&gt;LKML patchwork instance&lt;/a&gt; to perform matching and
tracking, and the &lt;a class="reference external" href="https://git.kernel.org/pub/scm/linux/kernel/git/mricon/korg-helpers.git/tree/git-patchwork-bot.py"&gt;source code&lt;/a&gt; for the bot is also available if you
would like to suggest improvements.&lt;/p&gt;
</summary></entry><entry><title>List archives on lore.kernel.org</title><link href="https://www.kernel.org/lore.html" rel="alternate"></link><published>2018-12-12T00:00:00+00:00</published><updated>2018-12-12T00:00:00+00:00</updated><author><name></name></author><id>tag:https://www.kernel.org,2018-12-12:lore.html</id><summary type="html">&lt;p&gt;You may access the archives of many Linux development mailing lists on
&lt;a class="reference external" href="https://lore.kernel.org/lists.html"&gt;lore.kernel.org&lt;/a&gt;. Most of them include a full archive of messages going
back several decades.&lt;/p&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;&lt;a class="reference external" href="https://lore.kernel.org/lists.html"&gt;listing of currently hosted archives&lt;/a&gt;&lt;/li&gt;
&lt;/ul&gt;
&lt;p&gt;If you would like to suggest another kernel development mailing list to
be included in this list, please follow the instructions on the
following wiki page:&lt;/p&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;&lt;a class="reference external" href="https://korg.wiki.kernel.org/userdoc/lore"&gt;Adding list archives to lore.kernel.org&lt;/a&gt;&lt;/li&gt;
&lt;/ul&gt;
&lt;div class="section" id="archiving-software"&gt;
&lt;h2&gt;Archiving software&lt;/h2&gt;
&lt;p&gt;The software managing the archive is called &lt;a class="reference external" href="https://public-inbox.org/design_notes.html"&gt;Public Inbox&lt;/a&gt; and offers
the following features:&lt;/p&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;Fast, searchable web archives&lt;/li&gt;
&lt;li&gt;Atom feeds per list or per individual thread&lt;/li&gt;
&lt;li&gt;Downloadable mbox archives to make replying easy&lt;/li&gt;
&lt;li&gt;Git-backed archival mechanism you can clone and pull&lt;/li&gt;
&lt;li&gt;Read-only nntp gateway&lt;/li&gt;
&lt;/ul&gt;
&lt;p&gt;We collected many list archives going as far back as 1998, and they are
now all available to anyone via a simple git clone. We would like to
extend our thanks to everyone who helped in this effort by donating
their personal archives.&lt;/p&gt;
&lt;/div&gt;
&lt;div class="section" id="obtaining-full-list-archives"&gt;
&lt;h2&gt;Obtaining full list archives&lt;/h2&gt;
&lt;p&gt;Git clone URLs are provided at the bottom of each page. Note, that due
mailing list volume, list archives are sharded into multiple
repositories, each roughly 1GB in size. In addition to cloning from
lore.kernel.org, you may also access these repositories on
&lt;a class="reference external" href="https://erol.kernel.org/"&gt;erol.kernel.org&lt;/a&gt;.&lt;/p&gt;
&lt;div class="section" id="mirroring"&gt;
&lt;h3&gt;Mirroring&lt;/h3&gt;
&lt;p&gt;You can continuously mirror the entire mailing list archive collection
by using the &lt;a class="reference external" href="https://github.com/mricon/grokmirror"&gt;grokmirror&lt;/a&gt; tool. The following repos.conf file should get
you all you need:&lt;/p&gt;
&lt;pre class="literal-block"&gt;
[lore.kernel.org]
site = https://lore.kernel.org
manifest = https://lore.kernel.org/manifest.js.gz
toplevel = /path/to/your/local/folder
mymanifest = /path/to/your/local/folder/manifest.js.gz
pull_threads = 4
&lt;/pre&gt;
&lt;p&gt;Please note, that you will require at least 20+ GB of local storage. The
mirroring process only replicates the git repositories themselves -- if
you want to use public-inbox with them, you will need to run
&amp;quot;&lt;tt class="docutils literal"&gt;&lt;span class="pre"&gt;public-inbox-init&lt;/span&gt;&lt;/tt&gt;&amp;quot; and &amp;quot;&lt;tt class="docutils literal"&gt;&lt;span class="pre"&gt;public-inbox-index&lt;/span&gt;&lt;/tt&gt;&amp;quot; to create the
database files required for public-inbox operation.&lt;/p&gt;
&lt;/div&gt;
&lt;/div&gt;
&lt;div class="section" id="linking-to-list-discussions-from-commits"&gt;
&lt;h2&gt;Linking to list discussions from commits&lt;/h2&gt;
&lt;p&gt;If you need to reference a mailing list discussion inside code comments
or in a git commit message, please use the &amp;quot;permalink&amp;quot; URL provided by
public-inbox. It is available in the headers of each displayed message
or thread discussion. Alternatively, you can use a generic message-id
redirector in the form:&lt;/p&gt;
&lt;ul class="simple"&gt;
&lt;li&gt;&lt;a class="reference external" href="https://lore.kernel.org/r/message&amp;#64;id"&gt;https://lore.kernel.org/r/message&amp;#64;id&lt;/a&gt;&lt;/li&gt;
&lt;/ul&gt;
&lt;p&gt;That should display the message regardless in which mailing list archive
it's stored.&lt;/p&gt;
&lt;/div&gt;
</summary></entry></feed>
        """

        const val RSS_FEED_1 = """<?xml version="1.0"?>
<?xml-stylesheet title="CSS_formatting" type="text/css" href="css/rss.css"?>
<?xml-stylesheet title="XSL_formatting" type="text/xml" href="rss2html.xsl"?>
<rss version="2.0"
  xmlns:sy="http://purl.org/rss/1.0/modules/syndication/"
><channel>
<!-- Generated with Perl's XML::RSS::SimpleGen v11.11 -->

<link>https://tools.ietf.org/html/</link>
<title>New RFCs</title>
<description>Recently released Request For Comments documents</description>
<language>en</language>
<lastBuildDate>Mon, 23 Dec 2019 20:27:17 GMT</lastBuildDate>
<skipHours><hour>0</hour><hour>1</hour><hour>2</hour><hour>3</hour><hour>4</hour><hour>5</hour><hour>6</hour><hour>7</hour><hour>8</hour><hour>9</hour><hour>10</hour><hour>11</hour><hour>12</hour><hour>13</hour><hour>14</hour><hour>15</hour><hour>16</hour><hour>17</hour><hour>18</hour><hour>19</hour><hour>21</hour><hour>22</hour><hour>23</hour></skipHours>
<sy:updateFrequency>1</sy:updateFrequency>
<sy:updatePeriod>daily</sy:updatePeriod>
<sy:updateBase>1970-01-01T20:30+00:00</sy:updateBase>
<ttl>1440</ttl>
<webMaster>webmaster@tools.ietf.org</webMaster>
<docs>http://www.interglacial.com/rss/about.html</docs>

<item>
  <title>8632: A YANG Data Model for Alarm Management</title>
  <link>https://www.rfc-editor.org/info/rfc8632</link>
  <description>(163KB) This document defines a YANG module for alarm management. It includes functions for alarm-list management, alarm shelving, and notifications to inform management systems. There are also operations to manage the operator state of an alarm and administrative alarm procedures. The module carefully maps to relevant alarm standards.</description>
</item>


</channel></rss>
        """
    }
}