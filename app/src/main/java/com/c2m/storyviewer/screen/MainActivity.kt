package com.c2m.storyviewer.screen

import android.animation.Animator
import android.animation.ValueAnimator
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.SparseIntArray
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.c2m.storyviewer.R
import com.c2m.storyviewer.app.StoryApp
import com.c2m.storyviewer.customview.StoryPagerAdapter
import com.c2m.storyviewer.data.StoryUser
import com.c2m.storyviewer.databinding.ActivityMainBinding
import com.c2m.storyviewer.utils.CubeOutTransformer
import com.c2m.storyviewer.utils.StoryGenerator
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.offline.Downloader
import com.google.android.exoplayer2.offline.ProgressiveDownloader
import com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloader
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

class MainActivity : AppCompatActivity(),
    PageViewOperator {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: StoryPagerAdapter
    private var currentPage: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpPager()
    }

    override fun backPageView() {
        if (binding.viewPager.currentItem > 0) {
            try {
                fakeDrag(false)
            } catch (e: Exception) {
                //NO OP
            }
        }
    }

    override fun nextPageView() {
        if (binding.viewPager.currentItem + 1 < binding.viewPager.adapter?.count ?: 0) {
            try {
                fakeDrag(true)
            } catch (e: Exception) {
                //NO OP
            }
        } else {
            //there is no next story
            Toast.makeText(this, "All stories displayed.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setUpPager() {
        val storyUserList = StoryGenerator.generateStories()
        preLoadStories(storyUserList)

        pagerAdapter = StoryPagerAdapter(
            supportFragmentManager,
            storyUserList
        )
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.currentItem = currentPage
        binding.viewPager.setPageTransformer(
            true,
            CubeOutTransformer()
        )
        binding.viewPager.addOnPageChangeListener(object : PageChangeListener() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPage = position
            }

            override fun onPageScrollCanceled() {
                currentFragment()?.resumeCurrentStory()
            }
        })
    }

    private fun preLoadStories(storyUserList: ArrayList<StoryUser>) {
        val imageList = mutableListOf<String>()
        val videoList = mutableListOf<String>()

        storyUserList.forEach { storyUser ->
            storyUser.stories.forEach { story ->
                if (story.isVideo()) {
                    videoList.add(story.url)
                } else {
                    imageList.add(story.url)
                }
            }
        }
        preLoadVideos(videoList)
        preLoadImages(imageList)
    }

    private fun preLoadVideos(videoList: MutableList<String>) {
        videoList.map { data ->
            GlobalScope.async {
                val dataUri = Uri.parse(data)
                val listener =
                    Downloader.ProgressListener { requestLength: Long, bytesCached: Long, _: Float ->
                        val downloadPercentage = (bytesCached * 100.0
                                / requestLength)
                        Log.d("preLoadVideos", "downloadPercentage: $downloadPercentage")
                    }

                val defaultDataSourceFactory = DefaultDataSourceFactory(
                    applicationContext,
                    Util.getUserAgent(applicationContext, getString(R.string.app_name))
                )
               val cacheDataSource = CacheDataSource.Factory()
                    .setCache(StoryApp.simpleCache!!).
                    setUpstreamDataSourceFactory(defaultDataSourceFactory)
                    .setCacheWriteDataSinkFactory(null)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                try {
                    ProgressiveDownloader(MediaItem.fromUri(dataUri), cacheDataSource).download(listener)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun preLoadImages(imageList: MutableList<String>) {
        imageList.forEach { imageStory ->
            Glide.with(this).load(imageStory).preload()
        }
    }

    private fun currentFragment(): StoryDisplayFragment? {
        return pagerAdapter.findFragmentByPosition(binding.viewPager, currentPage) as StoryDisplayFragment
    }

    /**
     * Change ViewPage sliding programmatically(not using reflection).
     * https://tech.dely.jp/entry/2018/12/13/110000
     * What for?
     * setCurrentItem(int, boolean) changes too fast. And it cannot set animation duration.
     */
    private var prevDragPosition = 0

    private fun fakeDrag(forward: Boolean) {
        if (prevDragPosition == 0 && binding.viewPager.beginFakeDrag()) {
            ValueAnimator.ofInt(0, binding.viewPager.width).apply {
                duration = 400L
                interpolator = FastOutSlowInInterpolator()
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(p0: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        removeAllUpdateListeners()
                        if (binding.viewPager.isFakeDragging) {
                            binding.viewPager.endFakeDrag()
                        }
                        prevDragPosition = 0
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        removeAllUpdateListeners()
                        if (binding.viewPager.isFakeDragging) {
                            binding.viewPager.endFakeDrag()
                        }
                        prevDragPosition = 0
                    }

                    override fun onAnimationStart(p0: Animator) {}
                })
                addUpdateListener {
                    if (!binding.viewPager.isFakeDragging) return@addUpdateListener
                    val dragPosition: Int = it.animatedValue as Int
                    val dragOffset: Float =
                        ((dragPosition - prevDragPosition) * if (forward) -1 else 1).toFloat()
                    prevDragPosition = dragPosition
                    binding.viewPager.fakeDragBy(dragOffset)
                }
            }.start()
        }
    }

    companion object {
        val progressState = SparseIntArray()
    }
}
