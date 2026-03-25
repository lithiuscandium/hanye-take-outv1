package fun.cyhgraph.mapper;

import fun.cyhgraph.entity.Rider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RiderMapper {

    @Select("select * from rider where status = 1 order by id")
    List<Rider> listOnline();

    @Select("select * from rider where id = #{id}")
    Rider getById(Integer id);

    @Update("update rider set current_node_id = #{currentNodeId}, update_time = now() where id = #{id}")
    void updateCurrentNode(Rider rider);
}

